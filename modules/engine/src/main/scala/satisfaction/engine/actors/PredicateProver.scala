package satisfaction
package engine
package actors

import GoalStatus._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.pattern.ask
import scala.concurrent.Await
import scala.concurrent.Future
import scala.collection._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import org.joda.time.DateTime
import track.TrackHistory


/**
 *  Actor who's responsibility is to satisfy a goal
 *
 *
 *  XXX Handle case to see if fully satisfied ( dependencies are satisfied)
 *  XXX  and cases where we want to force completion
 *
 */
class PredicateProver(val track : Track, val goal: Goal, val witness: Witness, val proverFactory: ActorRef) extends Actor with ActorLogging {

    ///private val dependencies: mutable.Map[String, ActorRef] = scala.collection.mutable.Map[String, ActorRef]()
    private val dependencies: mutable.Map[(Goal,Witness), ActorRef] = scala.collection.mutable.Map[(Goal,Witness), ActorRef]()
    private var jobRunner: ActorRef = null

    val status: GoalStatus = new GoalStatus(track.descriptor, goal.name, witness)

    private var listenerList: Set[ActorRef] = mutable.Set[ActorRef]()

    implicit val ec: ExecutionContext = ExecutionContext.global /// ???
    implicit val timeout = Timeout(5 minutes)
    
    var trackHistory : TrackHistory = null

    def receive = {

        case Satisfy =>
          try { 
            log.info(s" PredicateProver ${track.descriptor.trackName}::${goal.name} received Satisfy message witness is $witness ")
            log.info(s" Adding $sender to listener list")
            listenerList += sender
            if (goal.evidence != null &&
                goal.evidence.size != 0 &&
                goal.evidence.forall(e => e.exists(witness))) {
                log.info(s" Check Already satisfied ${goal.name} $witness ?? ")
                status.markTerminal( GoalState.AlreadySatisfied )
                //// Tell our Children 
                dependencies.foreach { 
                      case (predTuple, actor) =>  proverFactory ! new KillActor( predTuple._1.name, predTuple._2 ) 
                }
                publishSuccess
            } else {
                if (status.state != GoalState.Unstarted) {
                  //// XXX Test dependency on running job ...
                    sender ! InvalidRequest(status, "Job has already been started")
                } else {
                    /// Go through our dependencies, and ask them to
                    /// satify
                    if (dependencies.size > 0) {
                        dependencies.foreach {
                            case (pred, actor) =>
                                actor ! Satisfy
                        }
                        status.transitionState ( GoalState.WaitingOnDependencies)
                    } else {
                        runLocalJob()
                    }
                }
            }
          } catch {
            case unexpected : Throwable =>
              unexpected.printStackTrace
              log.error( "Unexpected exception while attempting to satisfy Goal ", unexpected) 
              
              status.markUnexpected( unexpected)

              publishFailure
          }

        case WhatsYourStatus =>
            //// Do a blocking call to just return  

            ///val currentStatus = new GoalStatus(goal, witness)
            ///currentStatus.state = status.state

            /// Go through and ask all our 
            val futureSet = dependencies.map {
                case (pred, actorRef) =>
                    (actorRef ask WhatsYourStatus).mapTo[StatusResponse]
            }
            Future.sequence(futureSet).map { statusList =>
                statusList.foreach { resp =>
                    status.addChildStatus(resp.goalStatus)
                }
            }
            sender ! StatusResponse(status)
        case RestartJob =>
          log.info(s" Received RestartJob Message ; Current state is ${status.state} " )
          status.state match {
            case GoalState.Failed |
                 GoalState.Aborted =>
              /// Restart our job Runner
               runLocalJob()
            case GoalState.DependencyFailed =>
                  dependencies.foreach {
                    case (pred, actor) =>
                              log.info(s"Checking actor $pred for Job Restart")
                              //// Sequentially ask the dependencies what they're status is 
                              val checkStatusF : Future[StatusResponse] = (actor ? WhatsYourStatus).mapTo[StatusResponse]
                              val checkStatus = Await.result(checkStatusF, Duration(30, SECONDS))
                              checkStatus match {
                                case GoalState.Failed =>
                                  log.info(s"Actor $pred has job failed; sending restart... ")
                                  actor ! RestartJob
                                case GoalState.DependencyFailed =>
                                  log.info(s"Actor $pred has dependency job failed; sending restart...")
                                  actor ! RestartJob
                                case GoalState.Aborted =>
                                  log.info(s"Actor $pred has been aborted; sending restart...")
                                  actor ! RestartJob
                                case _ =>
                                  /// don't restart if job hasn't failed 
                              }
                    }
                status.transitionState( GoalState.WaitingOnDependencies )
            case _ =>
              log.error( s"Invalid Request ; Job in state ${status.state} can not be restarted." )
              sender ! InvalidRequest(status,"Job needs to have failed in order to be restarted")
          }
          /// Make Abort fire and forget for now 
        case Abort(killChildren) =>
          log.info(" Received ABORT Message; State is " + status.state)
          status.state match {
            case GoalState.Success |
                 GoalState.AlreadySatisfied =>
           	  log.warning(s" Received Abort message, but state is ${status.state} ; Ignoring ." )
            case GoalState.Failed =>
               log.warning(s" Received Abort message after failure,  Killing self." )
               status.markTerminal(GoalState.Aborted )
               proverFactory ! ReleaseActor( goal.name, witness)
            case GoalState.Running =>
               /// If our job is running ... kill it 
              //// Check to see if abort was able to succeed ...
              log.info(" Received Abort message while Job is running; Killing Job ")
               status.markTerminal(GoalState.Aborted )
               jobRunner ! Abort
            //// check the status after attempting to abort the job
            case GoalState.DependencyFailed  |
            	 GoalState.Unstarted |
            	 GoalState.WaitingOnDependencies =>
            	   log.info("Received Abort while DependenciesFailed, or WaitingOnDependencies ")
            	   if(killChildren) {
            	     log.info("Killing all my children.")
                     dependencies.foreach {
                       case (pred, actor) =>
                         actor ! Abort
                     }
            	   }
              //// XXX TODO wait for all children's results
              ////sender ! GoalSuccess( status)
          }

        /// Messages which can be sent from children
        case GoalFailure(failedStatus) =>
            //// XXX 
            //// Should we try tp retry now ??? 
            //// or wait or rety agent to get back to us ???
            log.info(s" ${goal.name} Received Goal FAILURE ${failedStatus.state} from goal ${failedStatus.goalName}   ")
            status.addChildStatus(failedStatus)
            status.markTerminal(GoalState.DependencyFailed )
            publishFailure
        //// Add a flag to see if we want to 
        //// abort sibling jobs which may be running 
        case GoalSuccess(depStatus) =>
            log.info(s" ${goal.name} Received Goal Success ${depStatus.state} from goal ${depStatus.goalName}   ")
            if (depStatus != null)
                status.addChildStatus(depStatus)
            //// Determine if all jobs completed
            log.info( s" Received Deps = ${status.numReceivedStatuses} :: num Deps = ${dependencies.size} ")
            if (status.numReceivedStatuses  >= dependencies.size) {
               if(status.canProceed) {
                  runLocalJob()
               } else {
                 /// XXX One of our children failed 
                 log.info(s" Nope --- its not OK to continue")
               }
            }
        case JobRunSuccess(result) =>
            log.info(s" ${goal.name} Received Goal Satisfied from ${result.executionName} , send to our parent  ")
            status.markExecution(result)
            publishSuccess 
        case JobRunFailed(result) =>
            log.info(s" ${goal.name} Received Goal Failed from ${result.executionName} , send to our parent  ")
            status.markExecution(result)
            publishFailure

        case InvalidRequest =>
          
        case unexpected : Any => {
          log.error(" Received Unexpected message " + unexpected)
          log.warning(" Received Unexpected message " + unexpected)
          log.info(" Received Unexpected message " + unexpected)
        }
    }

    def publishSuccess = {
        ////proverFactory ! Success(status)

        ///val f = proverFactory ? GetListeners(goal, witness)
        ///val response = Await.result(f, Duration(30, SECONDS))
        ///response.asInstanceOf[Set[ActorRef]].foreach { lref =>
        ///lref ! Success(status)
        ///}
        listenerList.foreach{ actor: ActorRef =>
            log.info(s" Sending GoalSuccess to $actor ")
            actor ! GoalSuccess(status)
        }
        
        proverFactory ! GoalSuccess( status)
    }
    def publishFailure = {
        listenerList.foreach{ actor: ActorRef =>
            actor ! GoalFailure(status)
        }
        proverFactory ! GoalFailure( status)
    }

    def runLocalJob() {
        if (status.state != GoalState.Running) {
            status.transitionState( GoalState.Running )
            goal.satisfier match {
                case Some(satisfier) =>
                  if( jobRunner == null) {
                    val jobRunActor = Props(new JobRunner(satisfier, track ,goal, witness, witness))
                    this.jobRunner = context.system.actorOf((jobRunActor), "Satisfier_" + ProofEngine.getActorName(goal, witness))
                  }
                    jobRunner ! Satisfy
                    satisfier match {
                      case progressable : Progressable =>
                        log.info(s" Grabbing Progress for current satisfier for $goal.name -- progress $progressable.progressCounter ")
                       this.status.setProgressCounter( progressable.progressCounter )
                      case _ =>
                        log.info(s" Unable to determine progress for goal $goal.name ")
                    }
                case None =>
                   if( jobRunner == null) {
                    val jobRunActor = Props(new DefaultGoalSatisfier(
                            track,goal,
                           immutable.Set(goal.evidence.toSeq: _*), witness))
                    this.jobRunner = context.system.actorOf(jobRunActor)
                   }
                    jobRunner ! Satisfy
            }
        }
    }


    override def preStart() = {

        //// Create actors for all the sub actors, recursively
        if (goal.dependencies != null) {
            goal.dependencies.foreach{
                case (wmap: (Witness => Witness), subGoal: Goal) =>
                    //// Generate different witness, based on the mapping function
                    val newWitness = wmap(witness)
                    ///val depPredicate = subGoal.getPredicateString(newWitness)
                    val depProverRef = ProverFactory.getProver(proverFactory, track, subGoal, newWitness)
                    dependencies += ( (subGoal,newWitness) -> depProverRef)
            }
        }

    }

}