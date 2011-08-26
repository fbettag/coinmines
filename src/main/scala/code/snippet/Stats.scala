/*
 *  Copyright (c) 2011, Franz Bettag <franz@bett.ag>
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * All advertising materials mentioning features or use of this software
 *       must display the following acknowledgement:
 *       This product includes software developed by the Bettag Systems UG
 *       and its contributors.
 *
 *  THIS SOFTWARE IS PROVIDED BY BETTAG SYSTEMS UG ''AS IS'' AND ANY
 *  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL BETTAG SYSTEMS UG BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package code
package snippet

import java.util.Date
import scala.xml._

import net.liftweb._
import net.liftweb.http._
import net.liftweb.http.js._
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.SHtml._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.mapper._
import net.liftweb.common._

import model._

class Stats extends Loggable {

	/* helpers */
	def hashrate(user: Box[User]) = {
		var query = "SELECT SUM(hashrate) :: integer FROM pool_worker"
	
		user match {
			case Full(a: User) => query += " WHERE user_c = %s".format(a.id)
			case _ =>
		}

		try {
			DB.runQuery(query)._2.head.head.toFloat
		}
		catch {
			case _ => 0.0
		}
	}

	def poolworkers = PoolWorker.count(By_>(PoolWorker.hashrate, 0))
	

	/* snippets */
	def hostname = "*" #> S.hostName

	def global = {
		".global_hashrate *" #> "%.1f GH/sec".format(hashrate(Empty) / 1000.0) &
		".global_workers *" #> poolworkers.toString &
		".global_payout *" #> "%.2f BTC".format(50.00)

	}

	def user = {
		val user = User.currentUser.open_!
		".user_hashrate *" #> "%s MH/sec".format(user.hashrate.toFloat) &
		".user_shares_total *" #> user.shares_total.toString &
		".user_shares_round *" #> user.shares_round.toString &
		".user_shares_stale *" #> user.shares_stale.toString &
		".user_shares_round_estimate *" #> user.shares_round_estimate.toString &
		".user_payout *" #> "%.2f BTC".format(0.00)
	}

}
