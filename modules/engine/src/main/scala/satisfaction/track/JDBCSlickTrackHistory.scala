package satisfaction
package track

import java.sql.Timestamp
import slick.driver.H2Driver.api._
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.meta.MTable
import slick.lifted.ProvenShape
import org.joda.time._
import GoalStatus._
import satisfaction.track.Witness2Json._
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import slick.lifted.Query



/**
 * Using slick with H2 as our light-weight db
 */

case class DriverInfo(
	  val jdbcDriver : String =  "org.h2.Driver",
	  val dbURI : String = "jdbc:h2:file:data/jdbcTrackHistory", //change this to a file url, for persistence!
	  val user : String = "sa",
	  val passwd : String = "",
	  val props : java.util.Properties = new java.util.Properties
);


class JDBCSlickTrackHistory( val driverInfo : DriverInfo)   extends TrackHistory{
  import JDBCSlickTrackHistory._

	class TrackHistoryTable (tag: Tag) extends Table[GoalRun](tag, "TrackHistoryTable") {
  	  def id : Rep[Int]= column[Int]("id", O.PrimaryKey, O.AutoInc)
		  def trackName : Rep[String] = column[String]("trackName")
		  def forUser: Rep[String] = column[String]("forUser")
		  def version: Rep[String] = column[String]("version")
		  def variant: Rep[String] = column[String]("variant")
		  def goalName: Rep[String] = column[String]("goalName")
		  def witness: Rep[String] = column[String]("witness")
		  def startTime: Rep[Timestamp] = column[Timestamp]("startTime")
		  def endTime: Rep[Option[Timestamp]] = column[Option[Timestamp]]("endTime")
		  def state: Rep[String] = column[String]("state")
		  def parentId: Rep[Option[Int]] = column[Option[Int]]("parentId")
		  
		  def *  = (id,trackName,forUser,version,variant,goalName,witness,startTime,endTime,state,parentId) <>  ( goalRunTupled , goalRunUnapply) 
		}
	
	  val table : TableQuery[TrackHistoryTable] = TableQuery[TrackHistoryTable]
	  
	  val mainTable : String = "TrackHistoryTable"
	  val db = Database.forURL(driverInfo.dbURI,
	          driver = driverInfo.jdbcDriver, 
	          user=driverInfo.user, 
	          password=driverInfo.passwd,
	          prop = driverInfo.props)
	  ///val tblCreate = {
	      ///if ((dbAction{ MTable.getTables(mainTable) }).isEmpty) {
	    	 ///table.schema.create
	      ///}
	  ///}



	override def startRun(trackDesc : TrackDescriptor, goalName: String, witness: Witness, startTime: DateTime) : String =  dbAction {
		 (table returning table.map(_.id)) += GoalRun( trackDesc, goalName, witness, startTime )
	} .toString

	
	override def startSubGoalRun ( trackDesc: TrackDescriptor, goalName : String, witness: Witness, startTime : DateTime, parentRunId: String) : String = dbAction {
     (table returning table.map(_.id)) += GoalRun( trackDesc, goalName, witness, startTime, Some(parentRunId) )
	} .toString
	
	override def completeRun( id : String, state : GoalState.State) : String = dbAction {
	     val check = table.filter( _.id === id.toInt ).
	       map( x => ( x.state , x.endTime)).update( (state.toString, Some(new Timestamp(DateTime.now.getMillis))))
	         
	    check 
	} .toString
	
	
	def matchNone( c : Rep[String], strOpt: Option[String] ) : Rep[Boolean] = {
	  c match { 
	      case v if ( v == "None") => !strOpt.isDefined
	      case v if !( v == "None") => { strOpt.isDefined && v ==  strOpt.get }
	  }
	}
	
	
	def withinRange( g : TrackHistoryTable, startTime : Option[DateTime], endTime : Option[DateTime] ) : Rep[Boolean] = {
	  /***
				val aboveStart = 	(startTime match {
				  case Some(dateTime) => new DateTime(g.startTime).compareTo(dateTime.asInstanceOf[DateTime]) >= 0
   		    case None => true
				}) 
				val beforeEnd	=	(endTime match {
				  case Some(dateTime) => {
				     g.endTime match {
				       case Some(endDateStamp) => new DateTime(endDateStamp).isBefore(
				     }
				    if g.endTime.isDefined => new DateTime(g.endTime.get).compareTo(dateTime.asInstanceOf[DateTime]) <= 0
				  }
	 				case None => true
				})
			aboveStart && beforeEnd
			* 
			*/
	  /// XXX Handle null cases
	   ///g.startTime >= datetime2Timestamp(startTime.get) && g.endTime <= datetime2Timestamp(endTime.get )
	  /**
	  g.withFilter( 
	  g.filter( 
	  if( !g.endTime.isNull ) {
	      g.startTime >= datetime2Timestamp(startTime.get) && endTime <= dateTime2Timestamp( endTime.get)
	    
	  } else {
	      g.startTime >= datetime2Timestamp(startTime.get) 
	   }
	   * 
	   */
	      g.startTime >= datetime2Timestamp(startTime.get) 
	}
	
  implicit def datetime2Timestamp( dt : DateTime) : java.sql.Timestamp = {
     new Timestamp( dt.getMillis ) 
  }
  
  implicit def timestamp2DateTime( ts : Timestamp) : DateTime = {
     new DateTime( ts.getTime ) 
  }
	
	override def goalRunsForTrack(  trackDesc : TrackDescriptor , 
              startTime : Option[DateTime], endTime : Option[DateTime] ) : Seq[GoalRun] =  dbRun {
		     
   table.filter(g=>(g.trackName === trackDesc.trackName &&
		         								g.forUser === trackDesc.forUser &&
		         								g.version === trackDesc.version  &&
		         								matchNone(g.variant, trackDesc.variant) &&
		         								withinRange(g, startTime, endTime)
		   			 							))
	}
	
	override  def goalRunsForGoal(  trackDesc : TrackDescriptor ,  
              goalName : String,
              startTime : Option[DateTime], endTime : Option[DateTime] ) : Seq[GoalRun] = dbRun {
	  
		  table.filter(g=>(g.trackName === trackDesc.trackName &&
		         								g.forUser === trackDesc.forUser &&
		         								g.version === trackDesc.version &&
		         								matchNone(g.variant, trackDesc.variant) &&
		         								g.goalName == goalName &&
		         								withinRange( g, startTime, endTime )
		   			 							))
	}	
	
	override def lookupGoalRun(  trackDesc : TrackDescriptor ,  
              goalName : String,
              witness : Witness ) : Seq[GoalRun] = dbRun {
		     
		     table.filter(g => (g.trackName === trackDesc.trackName && 
		         										 	g.forUser === trackDesc.forUser &&
		         										 	g.version === trackDesc.version &&
		         								      matchNone(g.variant, trackDesc.variant) &&
		         										 	g.goalName === goalName &&
		         										 	g.witness === renderWitness(witness)
		    		 									))
	} 
	
	def lookupGoalRun( runID : String ) : Option[GoalRun] = dbRun { 
	     table.filter(_.id === runID.toInt)
	}.headOption
	
	def getAllHistory() : Seq[GoalRun] = dbRun {
		  table
	} 
	
	def getRecentHistory(): Seq[GoalRun] = dbRun {
	
	  val daysAgo = 7;
	  val dt = new DateTime();
	  val tsThreshold = new Timestamp(dt.minusDays(daysAgo).toDateMidnight().getMillis())
	    	
	      
	  table.filter( g => g.startTime > tsThreshold )
	} 
	
	def getParentRunId(runId: String) : Option[String] = dbRun {
	   table.filter(_.id === runId.toInt).map(gr => gr.parentId.toString) // might want throw exception is more than 1 result exists....
	}.headOption
	

  def dbRun[X]( f : => Query[_,X,Seq] ) : Seq[X] = {
	   val dbioF : Future[Seq[X]] = this.db.run( f.result ) 
	   Await.result( dbioF, 30 seconds )
	}
  
  def dbAction[X]( f : => DBIOAction[X,NoStream,Nothing] ) : X = {
	   val dbioF : Future[X] = this.db.run( f ) 
	   Await.result( dbioF, 30 seconds )
	}
  
  
  
}

object JDBCSlickTrackHistory extends JDBCSlickTrackHistory( new DriverInfo) {
 
  /// Define our mapping from row to our GoalRun case class for Slick
	def mapGoalRun2Table( g : GoalRun) = {
	  
	   Some((g.runId.get.toInt,
	     g.trackDescriptor.trackName,
	     g.trackDescriptor.forUser,
	     g.trackDescriptor.version,
	     g.trackDescriptor.variant.get,
	     g.goalName,
	     renderWitness(g.witness),
	     new java.sql.Timestamp(g.startTime.getMillis),
	     Some(new java.sql.Timestamp(g.endTime.get.getMillis)),
	     g.state.toString,
	     Some(g.parentRunId.get.toInt)
	     ))
	     
	}
  lazy val goalRunUnapply :      GoalRun => Option[(Int, String, String, String, String, String, String, java.sql.Timestamp, Option[java.sql.Timestamp], String, Option[Int])]  = mapGoalRun2Table
	

  def mapTable2GoalRun(id:Int,
      trackName:String, 
      forUser:String, 
      version:String,
      variant:String,
      goalName:String, 
      witness:String,
      startTime:Timestamp, 
      endTime:Option[Timestamp],
      state:String, 
      parentId:Option[Int]
	    ) : GoalRun = {
	     GoalRun(Some(id.toString), TrackDescriptor(trackName, forUser, version, Some(variant)), 
															       	    goalName, parseWitness(witness), new DateTime(startTime), 
															       	    endTime.flatMap(ts=> Some(new DateTime(ts))),
															       	    GoalState.withName(state),
															       	    parentId.flatMap( id => Some(id.toString) ) )
	}
	
  lazy val goalRunTupled : ( (Int, String, String, String, String, String, String, java.sql.Timestamp, Option[java.sql.Timestamp], String, Option[Int]) ) => GoalRun = {
    case tup => mapTable2GoalRun(tup._1,
              tup._2,
              tup._3,
              tup._4,
              tup._5,
              tup._6,
              tup._7,
              tup._8,
              tup._9,
              tup._10,
              tup._11
           )
  }
	
  
}