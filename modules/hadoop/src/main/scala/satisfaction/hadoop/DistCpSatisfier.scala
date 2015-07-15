package satisfaction
package hadoop

import satisfaction._
import hadoop._

import org.apache.hadoop.mapred.JobConf
import org.apache.hadoop.tools.DistCp
import org.apache.hadoop.util.ToolRunner
import org.joda.time.DateTime
import hdfs.Hdfs
import hdfs.HdfsImplicits
import hdfs.HdfsPath
import hdfs.VariablePath
import fs.FileSystem
import fs.Path
import Goal._
import org.apache.hadoop.mapred.JobClient
import org.apache.hadoop.mapred.JobStatus
import org.apache.hadoop.mapred.RunningJob
import org.apache.hadoop.tools.DistCpOptions
import org.apache.hadoop.fs.{Path => ApachePath}
import org.apache.hadoop.mapreduce.Job
import collection.JavaConversions._

/**
 *  For now deprecate DistCP Satisfier, 
 *   until we get a chance to better test it ..
 */
class DistCpSatisfier(val src: VariablePath, val dest: VariablePath)(implicit val track: Track) extends Satisfier with Logging  {

    override def name = s"DistCp $src to $dest "

    var _distCp : DistCp = null
    var _runningJob : Job = null


    def satisfy(projParams: Witness) :  ExecutionResult =  robustly {
            val srcPath: HdfsPath = src.getDataInstance(projParams).get.asInstanceOf[HdfsPath]
            val destPath: HdfsPath = dest.getDataInstance(projParams).get.asInstanceOf[HdfsPath]
           
            if (srcPath.path.equals(destPath)) {
                 info(s" Source $srcPath is equal to $destPath ; Doing nothing ")
                 true
            } else {
              
              
               if(destPath.hdfs.exists( destPath.path)) {
                  info(" Path $destPath already exists; Deleting $destPath ")
                  destPath.hdfs.delete( destPath.path) 
               }
               
               ////if( srcPath.path.name == destPath.path.name ) {
                  ///info(s"  Paths ${srcPath.path} and ${destPath.path} have same names ; distcp'ing to parent dest ${destPath.path.parent} ")
                 
                  ///val result = distcp( srcPath.path, destPath.path.parent )
                  ///info(s" Result of DistCp is $result")
                  ///result
               ////} else {
                  info(s"  Paths ${srcPath.path} and ${destPath.path} have different names ")
                  val result = distcp(srcPath.path, destPath.path);
                  //// Does DistCp have return codes ??
                  info(s" Result of DistCp is $result")
                  result
               ///}
            }
    } 

    
    /**
     * Determine if the job is our DistCp job
     */
    def isDistCpJob( js: JobStatus , jc: JobClient) : Boolean = {
       val checkJob: RunningJob =  jc.getJob( js.getJobID)
       //// Figure out proper Job name
       checkJob.getJobName.toLowerCase.contains("distcp")
    }
    
    override def abort() : ExecutionResult = robustly {
      if(_runningJob != null) {
         _runningJob.killJob()
     	 true
      } else {
        false
      }
    }
    

    def distcp(src : Path, dest : Path): Boolean = {
        val jobConf = new JobConf( Config( track) );
        jobConf.setSpeculativeExecution(false)


        val apacheSrc : ApachePath = HdfsImplicits.Path2ApachePath(src);
        val apacheDest : ApachePath = HdfsImplicits.Path2ApachePath(dest);
        ///val apacheDest : ApachePath = dest;

        val opts = new DistCpOptions(List[ApachePath]( apacheSrc),apacheDest)
        opts.setOverwrite(true)
        
        val distCp = new DistCp(jobConf, opts);
        _runningJob = distCp.execute();
        
        
        _runningJob.monitorAndPrintJob()
        _runningJob.isSuccessful
    }

}

object DistCpGoal {
   
    def apply(goalName: String, src: VariablePath, dest: VariablePath )
        (implicit  track: Track): Goal = {
        val srcVars = src.variables
        val destVars = dest.variables

        new Goal(
            name = goalName,
            satisfierFactory = SatisfierFactory( { new DistCpSatisfier(src, dest)  } ),
            variables = srcVars,
            dependencies = Set.empty,
            evidence = Set(dest)
        )
    }

}
