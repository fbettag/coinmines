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
package comet

import net.liftweb.http._
import net.liftweb.actor._
import net.liftweb.http.S._
import net.liftweb.http.SHtml._
import net.liftweb.http.js.JsCmds._
import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.common._

import org.joda.time._
import java.util.Date

import scala.xml._
import scala.collection.mutable.Map

import lib._
import model._

case object Tick

sealed trait StatsGatherCommand
case class StatsCleanup extends StatsGatherCommand
case class StatsGatherGlobal(target: CometActor) extends StatsGatherCommand
case class StatsGatherUser(target: CometActor, user: User) extends StatsGatherCommand

sealed trait StatsReply
case class StatsUserReply(
	user: User, lastUpdate: DateTime,
	hashrate: Int, total: Int, stale: Int,
	hashrateBtc: Int, totalBtc: Int, roundBtc: Int, staleBtc: Int,
	hashrateNmc: Int, totalNmc: Int, roundNmc: Int, staleNmc: Int,
	hashrateSlc: Int, totalSlc: Int, roundSlc: Int, staleSlc: Int,
	global: StatsGlobalReply) extends StatsReply

case class StatsGlobalReply(
	lastUpdate: DateTime,
	hashrate: Int, workers: Int,
	hashrateBtc: Int, sharesBtc: Int, payoutBtc: Double,
	hashrateNmc: Int, sharesNmc: Int, payoutNmc: Double,
	hashrateSlc: Int, sharesSlc: Int, payoutSlc: Double) extends StatsReply


object StatCollector extends LiftActor {
	def boot() {
		this ! Tick
	}

	// later, schedule itself for recalculation
	ActorPing.schedule(this, Tick, 1 minute)
	ActorPing.schedule(this, StatsCleanup, 10 minute)

	protected def messageHandler = {
		case a: StatsGatherGlobal =>
			a.target ! getGlobal
		case a: StatsGatherUser =>
			a.target ! getUser(a.user)
		case StatsCleanup =>
			cleanupJob
			ActorPing.schedule(this, StatsCleanup, 10 minute)
		case Tick =>
			minuteJob
			ActorPing.schedule(this, Tick, 1 minute)
		case _ =>
	}

	def currentDate = DateTimeHelpers.getDate
	def invalidDate = currentDate.minusMinutes(1).minusSeconds(1)
	def shortInvalidDate = currentDate.minusSeconds(30)

	private var globalReply = StatsGlobalReply(invalidDate, 0, 0, 0, 0, 0.0, 0, 0, 0.0, 0, 0, 0.0)
	private var userReplies: Map[String, StatsUserReply] = Map()

	private def cleanupJob {
		userReplies.map(r => if (r._2.lastUpdate.isBefore(currentDate.minusMinutes(30))) userReplies -= r._1)
	}

	private def minuteJob {
		try {
			DB.runQuery("""
				UPDATE pool_worker SET
				lasthash = (SELECT MAX(shares.timestamp_c) FROM shares WHERE pool_worker.username = shares.username),
				hashrate = (SELECT (COUNT(id) * 4294967296)/600/1000000 FROM shares WHERE shares.timestamp_c >= NOW() - interval '10 minutes' AND shares.username = pool_worker.username),
				hashrate_btc = (SELECT (COUNT(id) * 4294967296)/600/1000000 FROM shares WHERE shares.timestamp_c >= NOW() - interval '10 minutes' AND shares.username = pool_worker.username AND shares.network = 'bitcoin'),
				hashrate_nmc = (SELECT (COUNT(id) * 4294967296)/600/1000000 FROM shares WHERE shares.timestamp_c >= NOW() - interval '10 minutes' AND shares.username = pool_worker.username AND shares.network = 'namecoin'),
				hashrate_slc = (SELECT (COUNT(id) * 4294967296)/600/1000000 FROM shares WHERE shares.timestamp_c >= NOW() - interval '10 minutes' AND shares.username = pool_worker.username AND shares.network = 'solidcoin')
				RETURNING 0""")
		} catch { case _ => }

		def addBlock(net: String, diff: Double, block: Int): Boolean =
			NetworkBlock.find(By(NetworkBlock.network, net), By(NetworkBlock.blockNumber, block)) match {
				case Full(a: NetworkBlock) => true
				case _ => NetworkBlock.create.blockNumber(block).timestamp(currentDate.toDate).network(net).difficulty(diff).save
			}


		/* Update blocks */
		var diff = Coind.run(BtcCmd("getdifficulty")).toDouble
		var block = Coind.run(BtcCmd("getblocknumber")).toInt
		addBlock("bitcoin", diff, block)
		
		diff = Coind.run(NmcCmd("getdifficulty")).toDouble
		block = Coind.run(NmcCmd("getblocknumber")).toInt
		addBlock("namecoin", diff, block)
		
		diff = Coind.run(SlcCmd("getdifficulty")).toDouble
		block = Coind.run(SlcCmd("getblocknumber")).toInt
		addBlock("solidcoin", diff, block)
	}

	/* reload needed data */
	private def getUser(user: User): StatsUserReply = {
		def rawQuery(network: String, andWhere: String) = try {
			DB.runQuery("SELECT COUNT(id) FROM shares WHERE network = '%s' AND username LIKE '%s_%%' %s".format(network, user.email.is, andWhere))._2.first.first.toInt
		} catch { case _ => 0 }
		
		def shareQuery(network: String) = rawQuery(network, "")
		def staleQuery(network: String) = rawQuery(network, "AND our_result = false")

		userReplies.get(user.email) match {
			case Some(a: StatsUserReply) if (a.lastUpdate.isAfter(invalidDate)) => a
			case _ => {
				val btcShares = shareQuery("bitcoin")
				val nmcShares = shareQuery("namecoin")
				val slcShares = shareQuery("solidcoin")

				val btcStales = staleQuery("bitcoin")
				val nmcStales = staleQuery("namecoin")
				val slcStales = staleQuery("solidcoin")

				val repl: StatsUserReply = new StatsUserReply(user, currentDate,
					user.workers.foldLeft(0) { _ + _.hashrate },
					user.shares_total + btcShares + nmcShares + slcShares,
					user.shares_stale + btcStales + nmcStales + slcStales,
					user.workers.foldLeft(0) { _ + _.hashrate_btc },
					user.shares_total_btc + btcShares, btcShares, user.shares_stale_btc + btcStales,
					user.workers.foldLeft(0) { _ + _.hashrate_nmc },
					user.shares_total_nmc + nmcShares, nmcShares, user.shares_stale_nmc + nmcStales,
					user.workers.foldLeft(0) { _ + _.hashrate_slc },
					user.shares_total_slc + slcShares, slcShares, user.shares_stale_slc + slcStales,
					getGlobal)
				userReplies += (user.email.is -> repl)
				repl
			}
		}
	}

	private def getGlobal: StatsGlobalReply = {
		if (globalReply.lastUpdate.isBefore(shortInvalidDate)) {
			/* Shares */
			def sharesQuery(network: String) = try {
				DB.runQuery("SELECT SUM(id) FROM shares WHERE network = '%s'".format(network))._2.first.first.toInt
			} catch { case _ => 0 }

			val globalSharesBtc = sharesQuery("bitcoin")
			val globalSharesNmc = sharesQuery("namecoin")
			val globalSharesSlc = sharesQuery("solidcoin")

			/* Hashrate */
			def hashrateQuery(network: String) = try {
				DB.runQuery("SELECT (COUNT(id) * 4294967296)/600/1000000 FROM shares WHERE timestamp_c >= NOW() - interval '10 minutes' AND network = '%s'".format(network))._2.first.first.toInt
			} catch { case _ => 0 }

			val globalHashrateBtc = hashrateQuery("bitcoin")
			val globalHashrateNmc = hashrateQuery("namecoin")
			val globalHashrateSlc = hashrateQuery("solidcoin")
			val globalHashrate = globalHashrateBtc + globalHashrateNmc + globalHashrateSlc

			/* Workers */
			val globalWorkers = try {
				DB.runQuery("SELECT COUNT(id) FROM pool_worker WHERE lasthash > NOW() - interval '10 minutes'")._2.first.first.toInt
			} catch { case _ => 0 }

			globalReply = StatsGlobalReply(currentDate, globalHashrate, globalWorkers,
				globalHashrateBtc, globalSharesBtc, 0.0, // globalPayoutBtc
				globalHashrateNmc, globalSharesNmc, 0.0, // globalPayoutNmc
				globalHashrateSlc, globalSharesSlc, 0.0) // globalPayoutSlc
		}
		globalReply
	}

}

class StatComet extends CometActor {
	override def defaultPrefix = Full("stat")
  
	// Redraw every Minute
	ActorPing.schedule(this, Tick, 1 second)

	// If this actor is not used for 2 minutes, destroy it
	override def lifespan: Box[TimeSpan] = Full(2 minutes)

	override def lowPriority = {
		case Tick =>
			val msg: StatsGatherCommand = User.currentUser match {
				case Full(u: User) => StatsGatherUser(this, u)
				case _ => StatsGatherGlobal(this)
			}
			StatCollector ! msg

		case a: StatsGlobalReply =>
			partialUpdate(SetHtml("thestats", cssSel(a).apply(defaultHtml)))
			ActorPing.schedule(this, Tick, 100 seconds) 

		case a: StatsUserReply =>
			partialUpdate(SetHtml("thestats", cssSel(a).apply(defaultHtml)))
			ActorPing.schedule(this, Tick, 30 seconds) 
	}

	def render = <span id="thestats"/>

	def hashrate = 0
	def cssSel(r: StatsUserReply): CssSel =
		"#workerlist [style]" #> (if (r.hashrate > 0.000) "" else "display: none") &
		".miner_row *" #> r.user.workers.map(w =>
			".miner_name *" #> w.username &
			".miner_hashrate *" #> w.hashrate &
			//".miner_hashrate_btc *" #> "%s MH/s".format(w.hashrate_btc) &
			//".miner_hashrate_nmc *" #> "%s MH/s".format(w.hashrate_nmc) &
			//".miner_hashrate_slc *" #> "%s MH/s".format(w.hashrate_slc) &
			".miner_lasthash *" #> w.lasthash.toString 
		) &
		".user_hashrate *" #> "%s MH/sec".format(r.hashrate) &
		".user_shares_total *" #> r.total &
		".user_shares_stale *" #> r.stale &
		".user_btc_hashrate *" #> "%s MH/sec".format(r.hashrateBtc)  &
		".user_btc_shares_total *" #> r.totalBtc &
		".user_btc_shares_round *" #> r.roundBtc &
		".user_btc_shares_stale *" #> r.staleBtc &
		".user_btc_payout *" #> "%.8f BTC".format(0.toFloat) &
		".user_nmc_hashrate *" #> "%s MH/sec".format(r.hashrateNmc)  &
		".user_nmc_shares_total *" #> r.totalNmc &
		".user_nmc_shares_round *" #> r.roundNmc &
		".user_nmc_shares_stale *" #> r.staleNmc &
		".user_nmc_payout *" #> "%.8f NMC".format(0.toFloat) &
		".user_slc_hashrate *" #> "%s MH/sec".format(r.hashrateSlc)  &
		".user_slc_shares_total *" #> r.totalSlc &
		".user_slc_shares_round *" #> r.roundSlc &
		".user_slc_shares_stale *" #> r.staleSlc &
		".user_slc_payout *" #> "%.8f SLC".format(0.toFloat) &
		cssSel(r.global)

	def cssSel(r: StatsGlobalReply): CssSel =
		".last_updated *" #> <span>Last updated at<br/>{DateTimeHelpers.getDate.toString("yyyy-MM-dd HH:mm:ss")}</span> &
		".global_hashrate *" #> "%.1f GH/sec".format(r.hashrate / 1000.0)  &
		".global_workers *" #> r.workers &
		".global_btc_hashrate *" #> "%.3f GH/sec".format(r.hashrateBtc / 1000.0)  &
		".global_btc_shares *" #> r.sharesBtc &
		".global_btc_payout *" #> "%.8f BTC".format(0.toFloat) &
		".global_nmc_hashrate *" #> "%.3f GH/sec".format(r.hashrateNmc / 1000.0)  &
		".global_nmc_shares *" #> r.sharesNmc &
		".global_nmc_payout *" #> "%.8f NMC".format(0.toFloat) &
		".global_slc_hashrate *" #> "%.3f GH/sec".format(r.hashrateSlc / 1000.0)  &
		".global_slc_shares *" #> r.sharesSlc &
		".global_slc_payout *" #> "%.8f SLC".format(0.toFloat)
}

