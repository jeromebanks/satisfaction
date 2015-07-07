package satisfaction
package engine
package sla

import satisfaction.sla.SLAMonitored.SLAStatus._
import HealthMessages._
import akka.actor.ActorSystem
import scala.collection._
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import HealthMessages._
import scala.concurrent.ExecutionContext

/**
 *  SLAMonitor is an entry point, where one can ask the current status for 
 *    running jobs.
 */
class SLAMonitor(system : ActorSystem) {
     /// Need to use a mutable map here 
     private val monitorAgents : mutable.Map[String,ActorRef] = mutable.Map()
     
     
    implicit val timeout = Timeout( 3000 seconds )
        
    //// Use same execution context as ProofEngine
    implicit val ec = ExecutionContext.Implicits.global

    
  
     
     class RegisterAgent extends Actor {
         
         def receive = {
           case RegisterMonitor( desc, monitorAgent) => {
              monitorAgents.put( printJob(desc), monitorAgent) 
           }
           case UnregisterMonitor( desc) => {
             monitorAgents.remove( printJob(desc)) 
           }
         }
       
     } 
     
     val registerAgentActor : ActorRef = system.actorOf(Props(classOf[RegisterAgent]), "RegisterSLAMonitor")
     val registerAgentActorPath = registerAgentActor.path
     
    /**
     *  Get the global status for all jobs running in Satisfaction
     *    GREEN, if all jobs are GREEN,
     *    YELLOW, if any job is YELLOW,
     *    RED, if any job is RED
     */
    def getGlobalStatus : SLAStatus = {
  	   var isYellow = false
       getAllStatuses().foreach( { statusReport => {
          statusReport.desc._2.status match {
    	     case GREEN => { 
    	     }
             case YELLOW => {
                isYellow = true 
             }
             case RED => {
               return RED
             }
          }
        }
       })
      if(isYellow) {
        YELLOW
      } else {
        GREEN
      }
    }
    
    
    /**
     *  Return the sets of Tuples of TrackDescriptor,  goalname, and witness,
     *   which have the specified Status
     */
    def getJobsWithStatus( status : SLAStatus ) : Seq[StatusReport] = {
       getAllStatuses.filter(  _.desc._2.status ==  status)
    }
    
    def getAllStatuses() : Seq[StatusReport] = {
      val futureList = Future.sequence( monitorAgents.map( { case (jobDesc,actor) => {
           actor ? WhatsYourSLAStatus()
      } }).map( _.mapTo[StatusReport]) )
      
      Await.result( futureList, timeout.duration).toSeq
    }

    
    def getStatusForJob( job : JobDesc) : StatusReport = {
      monitorAgents.get( printJob(job)) match {
        case Some(agent) => {
           val statusFuture = agent ? WhatsYourSLAStatus() 
           Await.result(statusFuture, timeout.duration).asInstanceOf[StatusReport]
        }
        case None => {
          //// Need something if not monitored 
           new StatusReport( (job, null))
        }
          
      }
    }
}