package satisfaction
package hadoop
package hdfs

import org.specs2.mutable._
import satisfaction.Witness
import satisfaction._
import org.specs2.runner.JUnitRunner
import org.junit.runner.RunWith
import io._
import satisfaction.fs.Path
import org.apache.hadoop.conf.Configuration
import satisfaction.fs.LocalFileSystem

@RunWith(classOf[JUnitRunner])
class HdfsSpec extends Specification {
  
  
    
    "Hdfs" should {
<<<<<<< HEAD
      /**
        "create URLS starting with hdfs" in {
          //// XXX use MiniFS for unit testing ...
          /// Externalize configuration 
          val hdfsUrl = new java.net.URL("hdfs://dahdp2nn01/user/satisfaction/track/Sample/version_2.1/satisfaction.properties")
=======
        "create URLS starting with hdfs" in {
          //// XXX use MiniFS for unit testing ...
          /// Externalize configuration 
          val hdfsUrl = new java.net.URL("hdfs://dhdp2/user/satisfaction/track/Sample/version_2.1/satisfaction.properties")
>>>>>>> 133b57614f6c29c04c8e4fce1bc88320ef1adfbe
         
          val stream = hdfsUrl.openStream
          val props  = Substituter.readProperties( stream)
          
          true
        }
<<<<<<< HEAD
        * 
        */
=======
>>>>>>> 133b57614f6c29c04c8e4fce1bc88320ef1adfbe
        
        
        "List files" in {
          val hdfs = Hdfs.fromConfig(HdfsSpec.clientConfig)
          
<<<<<<< HEAD
          val path = new Path("hdfs://dahdp2nn01/data/ramblas/event_log")
=======
          val path = new Path("hdfs:///data/ramblas/event_log")
>>>>>>> 133b57614f6c29c04c8e4fce1bc88320ef1adfbe
          
          
          hdfs.listFiles( path ).foreach( fs => {
            System.out.println(s" Path is ${fs.path} ${fs.size} ${fs.lastAccessed}  ")
          } )
          
<<<<<<< HEAD
          val pathToday =  path / "20150414"
=======
          val pathToday =  path / "20140429"
>>>>>>> 133b57614f6c29c04c8e4fce1bc88320ef1adfbe
          hdfs.listFilesRecursively( pathToday ).foreach( fs => {
            System.out.println(s" Recursive Path is ${fs.path} ${fs.size} ${fs.lastAccessed}  ")
          } )

          hdfs.listFilesRecursively( path ).foreach( fs => {
            System.out.println(s" Path is ${fs.path} ${fs.size} ${fs.lastAccessed}  ")
          } )
          
          true
        }
        
<<<<<<< HEAD
        /**
=======
>>>>>>> 133b57614f6c29c04c8e4fce1bc88320ef1adfbe
        
        "access nameservice1" in {
          
          val testConf : Configuration = HdfsSpec.clientConfig
          testConf.writeXml(System.out)
          val haHdfs = Hdfs.fromConfig( testConf)
          
<<<<<<< HEAD
          val nsPath = new Path("hdfs://dahdp2nn01/user/ramblas/lib")
=======
          val nsPath = new Path("hdfs://dhdp2/user/ramblas/lib")
>>>>>>> 133b57614f6c29c04c8e4fce1bc88320ef1adfbe
          haHdfs.listFiles( nsPath ).foreach( fs => {
            System.out.println(s" Path is ${fs.path} ${fs.size} ${fs.lastAccessed}  ")
          } )
          
        }
        
        
        "read and write file" in {
           val hdfs = Hdfs.fromConfig( HdfsSpec.clientConfig)
           
<<<<<<< HEAD
           val brPath = Path("hdfs://dahdp2nn01/user/satisfaction/track/DauBackfill/version_0.2/auxJar/brickhouse-0.7.0-jdb-SNAPSHOT.jar")
=======
           val brPath = Path("hdfs://dhdp2/user/satisfaction/track/DauBackfill/version_0.2/auxJar/brickhouse-0.7.0-jdb-SNAPSHOT.jar")
>>>>>>> 133b57614f6c29c04c8e4fce1bc88320ef1adfbe

             val readFile = hdfs.readFile( brPath)
          
        }
        
        "read and write text file" in {
           val hdfs = Hdfs.fromConfig( HdfsSpec.clientConfig)
           
<<<<<<< HEAD
           val brPath = Path("hdfs://dahdp2nn01/user/satisfaction/track/DauBackfill/version_0.2/satisfaction.properties")
=======
           val brPath = Path("hdfs://dhdp2/user/satisfaction/track/DauBackfill/version_0.2/satisfaction.properties")
>>>>>>> 133b57614f6c29c04c8e4fce1bc88320ef1adfbe

             val readFile = hdfs.readFile( brPath)
             
             println(" Text file is  " + readFile)
          
        }
        
        
        "copy To Local" in {
          val brPath = Path("hdfs://dhdp2/user/satisfaction/track/DauBackfill/version_0.2/auxJar/brickhouse-0.7.0-jdb-SNAPSHOT.jar")
            
          val localPath =  Path("/tmp/hdfsTest" + System.currentTimeMillis()) / "brickhouse.jar"
          val localFS = LocalFileSystem
          
          val hdfs = Hdfs.fromConfig( HdfsSpec.clientConfig)
           
          hdfs.copyToFileSystem( localFS, brPath, localPath)
          
          val checkFile = new java.io.File( localPath.parent.toString )
          checkFile.exists must_== true
          checkFile.isDirectory must_== true

          val checkJar = new java.io.File( localPath.toString )
          checkJar.exists must_== true
          checkJar.isFile must_== true

          val lstat = localFS.getStatus(localPath)
          println( " JAr Size is " + lstat.size)
          lstat.size must_!= 0
        }
<<<<<<< HEAD
        * 
        */
=======
>>>>>>> 133b57614f6c29c04c8e4fce1bc88320ef1adfbe

    }

}

object HdfsSpec {
    
    def clientConfig: Configuration = {
      val conf = new Configuration
      val testPath = System.getProperty("user.dir") + "/modules/hadoop/src/test/resources/config/hdfs-site.xml"
      conf.addResource( new java.io.File(testPath).toURI().toURL())
      
      
       val nameService = conf.get("dfs.nameservices")
       if(nameService != null) {
         conf.set("fs.defaultFS", s"hdfs://$nameService")
       }
      conf
    }
}