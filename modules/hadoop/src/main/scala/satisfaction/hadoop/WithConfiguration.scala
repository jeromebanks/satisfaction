package satisfaction.hadoop

import org.apache.hadoop.conf.Configuration


/**
 *  Cake pattern for accessing Hadoop configuration object
 *  
 */

trait WithConfiguration {
  
    def configuration : Configuration
  
}