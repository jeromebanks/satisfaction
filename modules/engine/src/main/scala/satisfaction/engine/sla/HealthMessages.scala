package satisfaction
package engine
package sla

import org.joda.time.DateTime
import satisfaction.sla.SLAMonitored.SLAStatus._

/**
 *  Collection of Akka Messsages 
 *   sent, for SLA Tracking and 
 *    health reporting
 */
object HealthMessages {

  type  JobDesc = (TrackDescriptor,String,Witness) 
  
  case class WhatsYourStatus()
  case class CurrentStatus(status : SLAStatus, percentFinished : Double, estTimeArrival : DateTime)
  case class StatusReport( desc :(JobDesc,CurrentStatus)) 

}