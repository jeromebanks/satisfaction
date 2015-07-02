package satisfaction
package engine
package sla

 
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorRef
import akka.actor.ActorLogging
import akka.actor.Scheduler
import org.joda.time._
import actors._
import satisfaction.sla.SLAMonitored
import satisfaction.sla.SLAMonitored._
import satisfaction.sla.SLAMonitored.SLAStatus._
import track._
import satisfaction.track.TrackHistory.GoalRun
import satisfaction.track.TrackHistory.GoalRun

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
    
    
    import system.dispatcher
 
    val scheduler : Scheduler = system.scheduler
    
    
    lazy val expectedDurationTuple = calculateExpectedDuration
    
    lazy val expectedInterval = new Duration( expectedDurationTuple._1)
    lazy val durationStdDev = expectedDurationTuple._2
    
    
    def receive = {
      /**
       *  Message we send ourselves to check at a later time
       */
      case CheckSLA => {
        checkSLA()
      }
      case StatusResponse(goalStatus)  => {
        
      }
      case GoalFailure(goalStatus) =>
         completeRun(goalStatus)
      case GoalSuccess(goalStatus) =>
         completeRun(goalStatus)

      case unexpected : Any =>
        log.info(s" Received unexpected message $unexpected ")
      
    }
  

    def checkSLA() = {
       val now = DateTime.now
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
      
    }
    
    def completeRun(gs : GoalStatus) = {
      
      
    }

    /**
     *  Return the average duration and the standard deviation
     *    of the times
     */
    def calculateExpectedDuration() : (Long,Long) = {
       val filteredRuns  : Seq[GoalRun] = trackHistory.goalRunsForTrack(trackDesc,  None, None)
    		   .filter( _.goalName == goalName)
    		   .filter( _.endTime.isDefined)
    		   .filter( _.state == GoalState.Success).toList

       val sumCount : ( Long, Long ) = filteredRuns
            .foldLeft( (0l,0l) ) { (sumNumRows, goalRun : GoalRun)  => {
            val duration = new Interval( goalRun.startTime, goalRun.endTime.get)

            ( sumNumRows._1 + duration.toDurationMillis, sumNumRows._2 + 1 )
         }
       }
       
       if( sumCount._2 != 0) {
          val avg = sumCount._1 /sumCount._2
          
          val variance: Long = filteredRuns.map( {g: GoalRun => { 
            val diff= new Interval( g.startTime, g.endTime.get).toDurationMillis - avg
            diff*diff
          }}).sum
         
         val stdDev =  Math.sqrt(variance/sumCount._2)
         ( avg.toLong, stdDev.toLong)
          
       } else {
         ( 0, 0)
       }
    }
    
    
    def scheduleCheckSLA() = {
       ///scheduler.scheduleOnce(self, new CheckSLA(), Duration. )
    }
    
    override def preStart( ) = {
       scheduleCheckSLA()    
    }
    
       
   override def postStop() = {
     //// Kill the forward actor ..
     context.system.stop( monitoredActor)
   }
  
}

case class CheckSLA()