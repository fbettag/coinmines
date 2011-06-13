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

class Worker extends Loggable {

	def user = { User.currentUser.open_! }
	def workers = { user.workers }

	def updateName(worker: PoolWorker, name: String) = {
//			worker.name(name).save
//			logger.info("Worker %s - New Name: %s".format(worker.id, name))
	}

	def updatePassword(worker: PoolWorker, password: String) = {
//			worker.password(password).save
//			logger.info("Worker %s - New Password".format(worker.id))
	}

	def buildWorkerTable(xhtml: NodeSeq) : NodeSeq = {
		logger.info("count: %s".format(workers.length))
		logger.info("email: %s".format(user.email))

		workers.flatMap({ worker =>
			bind("worker", chooseTemplate("workers", "row", xhtml),
				"user" -> Text("%s_".format(user.email)),
				"name" -> SHtml.ajaxText(worker.name, updateName(worker, _), "style" -> "width: 80px;"),
				"hashrate" -> Text(worker.hashrate.toString),
				"lasthash" -> Text(worker.lasthash.toString),
				"password" -> SHtml.ajaxText(worker.password, updatePassword(worker, _), "style" -> "width: 80px;")
			)
		})
	}

	def list(xhtml: NodeSeq) : NodeSeq = {

		def entryTable = buildWorkerTable(xhtml)

		def addWorker(name: String) {
			var worker = PoolWorker.create.user(user).name(name)
			worker.save
			logger.info("New Worker %s - Name: %s".format(worker.id, worker.name))
			JsCmds.SetHtml("worker_list", entryTable)
		}

		logger.info("------------")
		logger.info(entryTable)
		logger.info("------------")

		bind("workers", xhtml,
			"table" -> entryTable,
			"newname" -> SHtml.ajaxText("worker", addWorker)
		)
/*
		def updateWallet(wallet: String) = {
			workers.wallet(wallet).save
			logger.info("workers %s - New Wallet: %s".format(workers.id, wallet))
		}

		def updateEmail(email: String) = {
			workers.email(email).save
			logger.info("workers %s - New E-Mail: %s".format(workers.id, email))
		}

		def updateDonation(donation: String) = {
			var donatePercent = donation.replaceFirst(",", ".")
			workers.donatePercent.setFromString(donatePercent)
			workers.save
			logger.info("workers %s - New Donation: %s %%".format(workers.id, donatePercent))
		}
		
		def updatePayoutlock(bool: Boolean) = {
			workers.payoutlock(bool).save
			logger.info("workers %s - Payout Lock: %s".format(workers.id, bool))
		}
	
		def updateIdlewarning(bool: Boolean) = {
			workers.idlewarning(bool).save
			logger.info("workers %s - Idle Warning: %s".format(workers.id, bool))
		}

*/
	}

}

