package controllers

import play.api._
import play.api.mvc._
import Results._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import com.klout.satisfaction.executor.api._
import com.klout.satisfaction.Project
import com.klout.satisfaction.Goal
import models.PlumbGraph
import models._
import com.klout.satisfaction.executor.actors.GoalStatus
import collection._

object ProjectPage extends Controller {

    def showProject(projName: String) = Action {
        ///val project = SyncApi.getProject(projName)
        val project = getSimpleProject
        //val internalGoals = project.project.get.internalGoals.toList
        //val externalGoals = project.project.get.externalGoals.toList
        ///val internalGoals = project.topLevelGoals.toList
        val internalGoals = project.allGoals.toList
        val externalGoals = project.externalGoals.toList

        Ok(views.html.showproject(projName, internalGoals map (_.name), externalGoals map (_.name)))

    }

    def getSimpleProject(): Project = {
        //// XXX 
        //// TODO 
        ///com.klout.satisfaction.projects.MaxwellProject.Project
        com.klout.satisfaction.projects.SampleProject()
    }

    //// Where does layout code belong ???
    def getPlumbGraphForGoal(topLevelGoal: Goal): PlumbGraph = {
        val pg = new PlumbGraph
        pg.divWidth = 2000
        pg.divHeight = 500
        val topNodeDiv = new PlumbGraph.NodeDiv(
            divContent = topLevelGoal.name
        )
        topNodeDiv.width = 10
        topNodeDiv.height = 10
        topNodeDiv.posX = 25
        topNodeDiv.posY = 2

        pg.addNodeDiv(topNodeDiv)
        var cnt: Int = 0
        topLevelGoal.dependencies.foreach { depGoal =>
            val depNode = new PlumbGraph.NodeDiv(
                divContent = depGoal._2.name
            )
            depNode.width = 10
            depNode.height = 10
            depNode.posY = 20
            depNode.posX = 5 + cnt * 12
            cnt += 1
            pg.addNodeDiv(depNode)
            pg.addConnection(depNode, topNodeDiv)
        }
        pg
    }

    def getFullPlumbGraphForGoal(goal: Goal): PlumbGraph = {
        val pg = new PlumbGraph
        pg.divWidth = 2000
        pg.divHeight = 500
        val nodeMap: mutable.Map[String, PlumbGraph.NodeDiv] = mutable.Map()

        val topNodeDiv = new PlumbGraph.NodeDiv(
            divContent = goal.name
        )
        topNodeDiv.width = 10
        topNodeDiv.height = 10
        topNodeDiv.posX = 25
        topNodeDiv.posY = 2
        pg.addNodeDiv(topNodeDiv)

        getPlumbGraphForGoalRecursive(pg, goal, topNodeDiv, nodeMap)

        pg
    }

    /**
     *  Do recursive layout of dependencies
     */
    def getPlumbGraphForGoalRecursive(pg: PlumbGraph, currentGoal: Goal, currentNode: PlumbGraph.NodeDiv, nodeMap: mutable.Map[String, PlumbGraph.NodeDiv]): Unit = {
        val numDeps = currentGoal.dependencies.size
        var startX = currentNode.posX - (currentNode.width + 2) * numDeps / 2
        currentGoal.dependencies.foreach { depGoal =>
            var depNode: PlumbGraph.NodeDiv = null
            if (nodeMap.contains(depGoal._2.name))
                depNode = nodeMap.get(depGoal._2.name).get
            else {
                depNode = new PlumbGraph.NodeDiv(divContent = depGoal._2.name)
                nodeMap.put(depGoal._2.name, depNode)
                pg.addNodeDiv(depNode)
            }

            depNode.width = currentNode.width
            depNode.height = currentNode.height
            depNode.posY = currentNode.posY + currentNode.height + 3
            depNode.posX = startX + depNode.width + 3
            startX = depNode.posX
            pg.addConnection(depNode, currentNode)

            getPlumbGraphForGoalRecursive(pg, depGoal._2, depNode, nodeMap)
        }

    }

}