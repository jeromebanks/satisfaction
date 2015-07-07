package satisfaction
package engine
package sla

 
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorRef
import akka.actor.ActorLogging
import akka.actor.Scheduler
import scala.concurrent.duration.{Duration => ActorDuration }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.TimeUnit
import org.joda.time._
import actors._
import satisfaction.sla.SLAMonitored
import satisfaction.sla.SLAMonitored._
import satisfaction.sla.SLAMonitored.SLAStatus._
import track._
import satisfaction.track.TrackHistory.GoalRun
import akka.actor.Cancellable
import HealthMessages._
import akka.actor.ActorPath
import akka.actor.ActorSelection

/** 
 *    Actor which intercepts Satisfy and PublishSuccess, PublishFailure
 *      methods, and tracks whether the job is meeting SLA.
 *      
 *    For now, just use the TrackHistory, and check how long after the 
 *     average time it is taking.
 *    Later consider the ProgressCounter, and see if it will finish
 *      before some hard SLA ( i.e. of the parent process )
 */
class SLAMonitorAgent( val monitoredActor : ActorRef, trackDesc : TrackDescriptor, goalName : String, witness : Witness, slaMonitored : SLAMonitored, trackHistory : TrackHistory)
     ( implicit system : ActorSystem)
    extends Actor with ActorLogging {
    private var _startSatisfyTime : DateTime = null
    
    private var _currentStatus  = SLAStatus.GREEN
    private var _statusScore : Double = 1.0
    private var _cancelSchedule : Cancellable = null
    
    case class CheckSLA()
    
    import system.dispatcher
 
    val scheduler : Scheduler = system.scheduler
    
    
    val jobDesc : JobDesc = (trackDesc,goalName,witness)
    
    
    lazy val expectedDurationTuple = trackHistory.calculateExpectedDuration(trackDesc, goalName)
    
    lazy val expectedInterval = new Duration( expectedDurationTuple._1)
    lazy val durationStdDev = expectedDurationTuple._2

    
    
    def receive = {
      /**
       *  Message we send ourselves to check at a later time
       */
      case CheckSLA => {
          //// If we haven't started running yet,
          ////  Continue to check the status until it is running
    	  if( _startSatisfyTime == null ) {
    	     monitoredActor ! WhatsYourStatus()
    	  } else {
    	      //// Otherwise check our SLA
    		  checkSLA( DateTime.now)
    	  }
      }
      case StatusResponse(goalStatus)  => {
        if( goalStatus.isTerminal) {
          checkSLA( goalStatus.timeFinished)
          completeRun(goalStatus)
        }
        goalStatus.state match {
          case GoalState.Running => {
            if(_startSatisfyTime == null) {
            	_startSatisfyTime = goalStatus.timeStarted
            }
            checkSLA( DateTime.now)
          }
        }
      }
      case GoalFailure(goalStatus) =>
         completeRun(goalStatus)
      case GoalSuccess(goalStatus) =>
         completeRun(goalStatus)

      case unexpected : Any =>
        log.info(s" Received unexpected message $unexpected ")
      
    }
  

    def checkSLA( now : DateTime) = {
       val interval = new Interval( _startSatisfyTime , now )
       if( interval.toDuration().isLongerThan( expectedInterval)) {
         val difference = interval.toDuration().minus( expectedInterval)
         if( slaMonitored.stdDeviationRed*durationStdDev >= difference.getMillis() ) {
        	_currentStatus = SLAStatus.RED
        	if( slaMonitored.notifyOnRed) {
        	   notifySlippage( _currentStatus, interval, difference)
        	}
         } else if( slaMonitored.stdDeviationYellow*durationStdDev >= difference.getMillis() ) {
        	_currentStatus = SLAStatus.YELLOW
        	if( slaMonitored.notifyOnYellow) {
        	   notifySlippage( _currentStatus, interval, difference)
        	}
         } else if( difference.isLongerThan( slaMonitored.maximumDuration)) {
        	_currentStatus = SLAStatus.RED
        	if( slaMonitored.notifyOnRed) {
        	   notifySlippage( _currentStatus, interval, difference)
        	}
         }
       } else {
    	   scheduleCheckSLA()
       }
    }
    
    
    def notifySlippage( slaStatus : SLAStatus, interval : Interval, difference : Duration ) = {
      slaMonitored.notifier.notify( )
    }
    
    def completeRun(gs : GoalStatus) = {
       _cancelSchedule.cancel
       //// Notify ?
    }


    def globalMonitor : ActorSelection = {
      system.actorSelection("/user/RegisterSLAMonitor" )
    }
    
    def scheduleCheckSLA() = {
       ///scheduler.scheduleOnce(self, new CheckSLA(), Duration. )
      _cancelSchedule = scheduler.schedule( ActorDuration( 5000,  "milliseconds"), ActorDuration(slaMonitored.checkPeriod.getMillis(), "milliseconds"), self, new CheckSLA  )
    }
    
    override def preStart( ) = {
       scheduleCheckSLA()    

       globalMonitor ! RegisterMonitor( jobDesc, self)
    }
    
       
   override def postStop() = {
       globalMonitor ! UnregisterMonitor( jobDesc)
   }
  
}
