package satisfaction
package engine
package sla

import satisfaction.sla.SLAMonitored.SLAStatus._
import HealthMessages._

/**
 *  SLAMonitor is an entry point, where one can ask the current status for 
 *    running jobs.
 */
class SLAMonitor {
  
  
    /**
     *  Get the global status for all jobs running in Satisfaction
     *    GREEN, if all jobs are GREEN,
     *    YELLOW, if any job is YELLOW,
     *    RED, if any job is RED
     */
    def getGlobalStatus : SLAStatus = {
      null
    }
    
    
    /**
     *  Return the sets of Tuples of TrackDescriptor,  goalname, and witness,
     *   which have the specified Status
     */
    def getJobsWithStatus( status : SLAStatus ) : Seq[JobDesc] = {
      null
    }
    
    def getAllStatuses() : Seq[(JobDesc,StatusReport)] = {
      null
    }

    
    def getStatusForJob( job : JobDesc) : StatusReport = {
      null 
    }
}