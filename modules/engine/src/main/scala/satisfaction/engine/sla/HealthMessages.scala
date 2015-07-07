package satisfaction
package engine
package sla

import org.joda.time.DateTime
import satisfaction.sla.SLAMonitored.SLAStatus._
import akka.actor.ActorRef

/**
 *  Collection of Akka Messsages 
 *   sent, for SLA Tracking and 
 *    health reporting
 */
object HealthMessages {

  type  JobDesc = (TrackDescriptor,String,Witness) 
  
  case class WhatsYourSLAStatus()
  case class CurrentStatus(status : SLAStatus, percentFinished : Double, estTimeArrival : DateTime)
  case class StatusReport( desc :(JobDesc,CurrentStatus)) 
  case class RegisterMonitor( desc : JobDesc, slaAgent : ActorRef )
  case class UnregisterMonitor( desc : JobDesc )

  def printJob( desc : JobDesc ) : String = {
     s" ${desc._1.trackName}::${desc._2}(${desc._3}) "
  }
  
  
  /// Make a constant if the job is not being monitored .. 
  val  NoSuchJob : StatusReport = new StatusReport((null,null));
    
}