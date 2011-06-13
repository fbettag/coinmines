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

	/* helpers */
	def addWorker(name: String) = {
		var worker = PoolWorker.create.user(user).username(user.email + "_" + name)
		worker.save
		logger.info("New Worker %s - Name: %s".format(worker.id, worker.username))
		js.jquery.JqJsCmds.AppendHtml("worker_list", buildRow(worker))
	}


	def updateName(worker: PoolWorker, name: String) = {
			logger.info("Worker %s - New Name: %s".format(worker.id, name))
			worker.username(user.email + "_" + name).save
			null
	}

	def updatePassword(worker: PoolWorker, password: String) = {
			logger.info("Worker %s - New Password".format(worker.id))
			worker.password(password).save
			null
	}

	def deleteWorker(worker: PoolWorker) = {
		logger.info("Worker %s - Delete: %s".format(worker.id, worker.username))
		worker.delete_!
		js.jquery.JqJsCmds.FadeOut("row_%s".format(worker.id), 200, 200)
	}

	/* snippets */
	def add = SHtml.ajaxText("new_worker", addWorker)

	def buildRow(worker: PoolWorker) = <xml:group>
		<tr id={"row_%s".format(worker.id)}>
			<td>{user.email}_{SHtml.ajaxText(worker.username.replaceFirst("^%s_".format(user.email), ""), updateName(worker, _))}</td>
			<td>{"%s MH/sec".format(worker.hashrate)}</td>
			<td>{worker.lasthash.toString}</td>
			<td>{SHtml.ajaxText(worker.password, updatePassword(worker, _), "class" -> "worker_password", "type" -> "password")}</td>
			<td>{a(() => deleteWorker(worker), <span>{S.??("delete")}</span>)}</td>
		</tr>
	</xml:group>

	def list =
		<table>
			<thead>
				<tr>
					<th>{S.??("Worker Name")}</th>
					<th>{S.??("Hashrate")}</th>
					<th>{S.??("Last Hash")}</th>
					<th>{S.??("Password")}</th>
					<th></th>
				</tr>
			</thead>
			<tbody id="worker_list">
				{workers.flatMap(buildRow _)}
			</tbody>
		</table>

}
