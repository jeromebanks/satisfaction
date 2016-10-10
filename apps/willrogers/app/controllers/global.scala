package willrogers;

import play.api._
import satisfaction._
import satisfaction.track.TrackFactory
import satisfaction.track.TrackScheduler
import satisfaction.fs.Path
import satisfaction.track.JDBCSlickTrackHistory
import satisfaction.engine.actors.ProofEngine
import play.api.mvc.RequestHeader
import play.api.mvc.Results._
import scala.concurrent.Future
import satisfaction.track.TrackHistory
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import satisfaction.track.TrackFactory.TracksUnavailableException
import satisfaction.fs.s3.WithS3
import awscala.Credentials
import satisfaction.fs.s3.WithAWSCredentials
////import satisfaction.hadoop.hdfs.WithHDFS



object Global extends play.api.GlobalSettings {

    ////implicit lazy val hiveConf = Config.config
    ////implicit lazy val metaStore : MetaStore  = new MetaStore()( hiveConf)

    ////lazy val hdfsFS = Hdfs.fromConfig( hiveConf )
    lazy val trackFactory : TrackFactory = {
          try {
             ///class HDFSTrackFactory extends TrackFactory with WithHDFS ;
             ///val tf = new TrackFactory( trackPath, Some(trackScheduler), Some(hadoopWitness)) with WithS3 with AWSCredentials {
             val tf = new TrackFactory( trackPath, Some(trackScheduler), None) with WithS3 with WithAWSCredentials {
                override def bucketName = "stitchfix.aa.default"
                override def credentials = Credentials( sys.env("AWS_ACCESS_KEY_ID"), sys.env("AWS_SECRET_ACCESS_KEY"))  
             }
             ///val tf = new TrackFactory with WithHDFS
             trackScheduler.trackFactory = tf
             tf.initializeAllTracks
             Logger.info(" Tracks initialized.")
             tf
          } catch {
           case noTracks : TracksUnavailableException =>
            Logger.warn(s" Unable to load tracks ${noTracks.getLocalizedMessage()} ")
            null
           case unexpected : Throwable => throw unexpected
         }
        }

    var trackPath : Path = new Path("/user/satisfaction")


    override def onStart(app: Application) {
        super.onStart( app)
        /// XXX initialize name-node, job-tracker, and metastore 
        //// with values from app.configuration 
        
        Logger.info(" Starting up Will Rogers;  I never metastore I didn't like ...")
        
        Logger.info(" Starting the Akka Actors")
        
        val initPe = proofEngine


        trackPath = trackPath( app.configuration)
        Logger.info(s" Using TrackPath $trackPath ")
        
       
        Logger.info("XXXXXXXXXXXX Creating GLOBAL TrackFactory YYYYYYYYYYYY")
        val initTF = trackFactory

    }
    
    def trackPath( playConfig  : Configuration) : Path = {
        playConfig.getString("satisfaction.track.path") match {
          case Some(trackPath) => Path(trackPath)
          case None => {
             val user = System.getProperty("user.name")
             user match {
               case "satisfaction" => Path("/user/satisfaction")
               case "root" => Path("/user/satisfaction")
               case _ => Path(s"/user/${user}/satisfaction")
             }
          }
        }
    }
    
    //// XXX Add Driver info ...
    
    lazy val trackHistory : TrackHistory = JDBCSlickTrackHistory
    lazy val proofEngine = new ProofEngine(Some(trackHistory))
    
    lazy val trackScheduler = new TrackScheduler(proofEngine)
    
    
    override def onError(request: RequestHeader, ex: Throwable) = {
       Future.successful(
         InternalServerError( views.html.errorPage(ex) )
       )
    }  
    
}