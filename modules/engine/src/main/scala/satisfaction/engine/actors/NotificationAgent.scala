package satisfaction
package engine
package actors

import satisfaction.notifier.Notifier
import akka.actor.Actor
import akka.actor.ActorLogging
import satisfaction.Track
import satisfaction.notifier.Notified
import GoalStatus._


/**
 *   First pass at notification 
 */
class NotificationAgent( notifier : Notifier )(implicit val track : Track ) extends Actor with ActorLogging {
  
     def notified : Notified =  {
        track match {
          case notified : Notified => {
              notified 
          }  
          case _ => {
            /// Shouldn't happen
            null
          }
        } 
     }
      
     def receive = {
        case GoalFailure(goalStatus) =>
          if( notified.notifyOnFailure)
            notify( goalStatus)
         /// Continue to listen on failure, 
         /// in case there are retries ...
        case GoalSuccess(goalStatus) =>
          if( notified.notifyOnSuccess)
            notify( goalStatus)
          /// No need to notify if job was successful ...
          stop()
     } 

     
     def notify( gs : GoalStatus ) = {
       log.info(s" Notifying result of ${gs.goalName} is ${gs.state} ")
       //// XXXX Do retry logic for notification errors ...
       //// XXX If email is temporarily down because of network issues,
       ////   we want to retry !!!
       try { 
          notifier.notify( gs.witness, gs.execResult )
       } catch {
         case unexpected : Throwable => {
           log.error("Unexpected error while notifying job status ", unexpected)
         } 
       }
     }
     
     def stop() = {
        context.stop( context.self)
     }

}