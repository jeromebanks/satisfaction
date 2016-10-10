package satisfaction
package hadoop.hdfs

import fs._

import hadoop.WithConfiguration
/**
 *   Having a Hadoop HDFS as the external distributed FS
 */
trait WithHDFS  extends WithFS {
   this : WithConfiguration => { 

      def dfs =  Hdfs.fromConfig( configuration)
      
   }
}