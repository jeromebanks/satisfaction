package satisfaction
package sla

import satisfaction.notifier.Notifier
import org.joda.time.Period
import org.joda.time.DateTime
import org.joda.time.Duration


     /**
     *  Define the various SLA Statuses
     *  which are possible;
     *  RED, YELLOW or GREEN
     */
object SLAMonitored {
    object SLAStatus extends Enumeration  {
       type SLAStatus = Value
      
       val RED, YELLOW , GREEN = Value
    }
}
  
/**
 *  Trait to add to a track to so that SLA's
 *   will be  
 */
trait SLAMonitored {
  
    def notifier : Notifier 
    
    val stdDeviationYellow : Double = 1.0
    
    val stdDeviationRed : Double = 2.0
    
    /**
     *  Override to set to notify on going to 
     *    YELLOW status
     */
    val notifyOnYellow : Boolean = false
    
    /**
     *  Override to turn off notifications
     *  on turning to RED status
     */
    val notifyOnRed = true
    
    
    /***
     *  Frequency to check if SLA is met
     */
    val checkPeriod = Period.minutes( 5)

    /**
     *  Maximum time from starting 
     *   before RED Status is reached
     *   
     *   /// XXX TBD 
     */
    val maximumTimeOffset : Period = Period.hours(3)
    
    
    /**
     *  Maximimum time that a job should run
     */
    val maximumDuration : Duration = Duration.standardHours( 3)
    
    

}


object SLAMonitoredGoal {
  
  
    /**
     *  Allow Goals to be modified by a :: operator
     *  
     *    object LongSLA extends SLAMonitored {
     *       override val maximumDuration = Duration.standardHours(5 )
     *    }
     *    
     *    val goal = HiveGoal( ... ) :: LongSLA
     *    
     *    The :: Operator needs to be the last method called
     *     ( because copy() won't preserve the Trait property)
     */
    implicit def ::( goal : Goal, slaMonitored : SLAMonitored )(implicit track : Track) : Goal = {
        SLAMonitoredGoal( goal, slaMonitored) 
    }
  
    /// Add a trait to a goal
    def apply( goal : Goal , slaMonitored : SLAMonitored)(implicit track : Track) : Goal = {
       
         new Goal( goal.name,
                goal.satisfierFactory,
                goal.variables,
                goal.dependencies,
                goal.evidence ) with SLAMonitored {
             override val notifier = slaMonitored.notifier
             override val stdDeviationYellow = slaMonitored.stdDeviationYellow
             override val stdDeviationRed = slaMonitored.stdDeviationRed
    
             override val notifyOnYellow : Boolean = slaMonitored.notifyOnYellow
             override val notifyOnRed : Boolean = slaMonitored.notifyOnRed
             override val checkPeriod = slaMonitored.checkPeriod
             override val maximumTimeOffset = slaMonitored.maximumTimeOffset
             override val maximumDuration = slaMonitored.maximumDuration
         }
    }

}

    

  