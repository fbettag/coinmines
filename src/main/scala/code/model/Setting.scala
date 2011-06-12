package code
package model

import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.common._

object Setting extends Setting with LongKeyedMetaMapper[Setting] {
	override def dbTableName = "settings"
	override def fieldOrder = List(setting, value)
}

class Setting extends LongKeyedMapper[Setting] with IdPK {
	def getSingleton = Setting

	object setting extends MappedString(this, 255) {
		override def dbIndexed_? = true
		override def dbNotNull_? = true
	}

	object value extends MappedString(this, 255) {
		override def dbIndexed_? = true
	}
}
