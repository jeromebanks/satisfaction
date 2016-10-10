package satisfaction
package fs

/**
 *  Trait to allow dependency injection of 
 *    different FS implementations
 */
trait WithFS {
  
   /// File 
   def dfs : FileSystem
  
}
