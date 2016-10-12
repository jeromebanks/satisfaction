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


	class TrackHistoryTable (tag: Tag) extends Table[(Int, String, String, String, String, String, String, Timestamp, Option[Timestamp], String, Option[String])](tag, "TrackHistoryTable") {
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
		  def parentId: Rep[Option[String]] = column[Option[String]]("parentId")
		  
		  def * : ProvenShape[(Int, String, String, String, String, String, String, Timestamp, Option[Timestamp], String, Option[String])] = (id, trackName, forUser, version, variant, goalName, witness, startTime, endTime, state, parentId)
		}
	
	  val table : TableQuery[TrackHistoryTable] = TableQuery[TrackHistoryTable]
	  
	  val mainTable : String = "TrackHistoryTable"
	  val db = Database.forURL(driverInfo.dbURI,
	          driver = driverInfo.jdbcDriver, 
	          user=driverInfo.user, 
	          password=driverInfo.passwd,
	          prop = driverInfo.props)
	  val tblCreate = {
	      if (dbRun({ MTable.getTables(mainTable) }).isEmpty) {
	    	 table.schema.create
	      }
	  }



	override def startRun(trackDesc : TrackDescriptor, goalName: String, witness: Witness, startTime: DateTime) : String =  dbAction {
		(table returning table.map(_.id)) += 
		  (1, trackDesc.trackName, trackDesc.forUser, trackDesc.version, trackDesc.variant.toString(), 
			goalName, renderWitness(witness), new Timestamp(startTime.getMillis()), None, GoalState.Running.toString(), 
			None)
	} .toString

	
	override def startSubGoalRun ( trackDesc: TrackDescriptor, goalName : String, witness: Witness, startTime : DateTime, parentRunId: String) : String = dbAction {
     (table returning table.map(_.id)) += 
				  (1, trackDesc.trackName, trackDesc.forUser, trackDesc.version, trackDesc.variant.toString(), 
					goalName, renderWitness(witness), new Timestamp(startTime.getMillis()), None, GoalState.Running.toString(), Some(parentRunId))
	} .toString
	
	override def completeRun( id : String, state : GoalState.State) : String = dbAction {
	     val check = table.filter( _.id === id.toInt ).
	       map( x => ( x.state , x.endTime)).update( (state.toString, Some(new Timestamp(DateTime.now.getMillis))))
	         
	    check 
	} .toString
	
	implicit def mapTable2GoalRun( g : TrackHistoryTable) : GoalRun = {
	  /**
	  val gr = GoalRun(TrackDescriptor(g.trackName, g.forUser, g.version, Some(g.variant)), 
															       	    g.goalName, parseWitness(g.witness), new DateTime(g.startTime), 
															       	    g.endTime match { case Some(timestamp) => Some(new DateTime(timestamp))
															       	    			 case None => null}, GoalState.withName(g.state),
															       	    g.parentId match { case Some(id) => Some(id.toString)
															       	    			case None => null})
	  gr.runId = g.id.toString
		gr
		**/
	  null
	}
	
	def matchNone( c : Rep[String], strOpt: Option[String] ) : Rep[Boolean] = {
	  c match { 
	      case v if ( v == "None") => !strOpt.isDefined
	      case v if !( v == "None") => { strOpt.isDefined && v ==  strOpt.get }
	  }
	}
	
	def withinRange( g : TrackHistoryTable, startTime : Option[DateTime], endTime : Option[DateTime] ) : Rep[Boolean] = {
	  /**
				val aboveStart = 	(startTime match {
				  case Some(dateTime) => new DateTime(g.startTime).compareTo(dateTime.asInstanceOf[DateTime]) >= 0
   		    case None => true
				}) 
				val beforeEnd	=	(endTime match {
				  case Some(dateTime) if g.endTime.isDefined => new DateTime(g.endTime.get).compareTo(dateTime.asInstanceOf[DateTime]) <= 0
	 				case Some(dateTime) if !g.endTime.isDefined => false
	 				case None => true
				})
			aboveStart && beforeEnd
			* 
			*/
	  true
	}
	
	
	override def goalRunsForTrack(  trackDesc : TrackDescriptor , 
              startTime : Option[DateTime], endTime : Option[DateTime] ) : Seq[GoalRun] = dbRun {
		     
   table.filter(g=>(g.trackName === trackDesc.trackName &&
		         								g.forUser === trackDesc.forUser &&
		         								g.version === trackDesc.version  &&
		         								matchNone(g.variant, trackDesc.variant) &&
		         								withinRange(g, startTime, endTime)
		   			 							)).map(mapTable2GoalRun).seq
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
		   			 							)).map(mapTable2GoalRun).seq
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
		    		 									)).map(mapTable2GoalRun).seq
	} 
	
	def lookupGoalRun( runID : String ) : Option[GoalRun] = dbRun { 
	     val g = table.filter(_.id === runID.toInt)
	   	
	     if (!g.isEmpty) {
	    	 val trackDesc :TrackDescriptor = TrackDescriptor(g(0).trackName, g(0).forUser, g(0).version, Some(g(0).variant))
	     
		     val dtStart : DateTime = new DateTime(g(0).startTime)
		     val dtEnd = g(0).endTime match { 
		       case Some(timestamp) => Some(new DateTime(timestamp))
		       case None => None
		     }
	    	 val parentId = g(0).parentId match {
	    	   case Some(id) => Some(id.toString)
	    	   case None => None
	    	 }
		     val returnGoal = GoalRun(trackDesc, g(0).goalName, parseWitness(g(0).witness), dtStart, dtEnd, GoalState.withName(g(0).state), parentId)
		     returnGoal.runId = g(0).id.toString
		     Some(returnGoal)
	     } else {
	       None
	     }
	}

	
	
	def getAllHistory() : Seq[GoalRun] = dbRun {
		  table.map(mapTable2GoalRun).seq
	} 
	
	def getRecentHistory(): Seq[GoalRun] = dbRun {
	
	  val daysAgo = 7;
	  val dt = new DateTime();
	  val tsThreshold = new Timestamp(dt.minusDays(daysAgo).toDateMidnight().getMillis())
	    	
	      
	  table.filter( g => g.startTime > tsThreshold ).map(mapTable2GoalRun).seq
	} 
	
	def getParentRunId(runId: String) : Option[String] = dbRun {
	   val resultSet = table.filter(_.id === runId.toInt).map(gr => gr.parentId) // might want throw exception is more than 1 result exists....
	   resultSet.max.result
	}
	

  def dbRun[X]( f : => DBIO[X] ) : X = {
	   val dbioF : Future[X] = this.db.run( f ) 
	   Await( dbioF, 30 seconds )
	}
  
  def dbAction[X]( f : => DBIOAction[X,_,_] ) : X = {
	   val dbioF : Future[X] = this.db.run( f ) 
	   Await( dbioF, 30 seconds )
	}
  
  
  
}

object JDBCSlickTrackHistory extends JDBCSlickTrackHistory( new DriverInfo) {
 
  
}