package code
package model

import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.common._
import java.util.Date

object ShareHistory extends ShareHistory with LongKeyedMetaMapper[ShareHistory] {
	override def dbTableName = "shares_history"
	override def fieldOrder = List(user, counted, remoteHost, blockNumber, ourResult, upstreamResult, reason, solution, timestamp)
}

class ShareHistory extends LongKeyedMapper[ShareHistory] with IdPK {
	def getSingleton = ShareHistory

	object user extends MappedLongForeignKey(this, User) {
		override def dbIndexed_? = true
		override def dbNotNull_? = true
	}

	object counted extends MappedBoolean(this) {
		override def dbNotNull_? = true
		override def defaultValue = true
	}

	object blockNumber extends MappedInt(this) {
		override def dbNotNull_? = true
	}

	object score extends MappedDecimal(this, java.math.MathContext.DECIMAL64, 2)

	object remoteHost extends MappedString(this, 255) {
		override def dbColumnName = "rem_host"
		override def dbNotNull_? = true
	}

	object ourResult extends MappedBoolean(this) {
		override def dbColumnName = "our_result"
		override def dbNotNull_? = true
		override def defaultValue = false
	}

	object upstreamResult extends MappedBoolean(this) {
		override def dbColumnName = "upstream_result"
		//override def dbNotNull_? = true
		//override def defaultValue = false
	}

	object reason extends MappedString(this, 255) {
//		override def dbNotNull_? = true
	}

	object solution extends MappedString(this, 1024) {
//		override def dbNotNull_? = true
	}

	object timestamp extends MappedDateTime(this) {
		override def dbNotNull_? = true
	}


}
