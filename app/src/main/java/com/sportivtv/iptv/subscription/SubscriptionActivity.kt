package com.sportivtv.iptv.subscription

import android.os.Bundle
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sportivtv.iptv.R
import com.sportivtv.iptv.model.Subscription
import kotlinx.coroutines.launch

/**
 * Subscription Management Activity.
 * Add, edit, delete, and test IPTV subscriptions.
 */
class SubscriptionActivity : AppCompatActivity() {

    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SubscriptionAdapter
    private lateinit var emptyState: TextView
    private lateinit var progressBar: ProgressBar

    // Add form fields
    private lateinit var nameEdit: EditText
    private lateinit var urlEdit: EditText
    private lateinit var epgUrlEdit: EditText
    private lateinit var usernameEdit: EditText
    private lateinit var passwordEdit: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        subscriptionManager = SubscriptionManager(this)

        initViews()
        refreshList()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.subscriptions_rv)
        emptyState = findViewById(R.id.sub_empty_state)
        progressBar = findViewById(R.id.sub_progress)
        nameEdit = findViewById(R.id.edit_sub_name)
        urlEdit = findViewById(R.id.edit_sub_url)
        epgUrlEdit = findViewById(R.id.edit_sub_epg_url)
        usernameEdit = findViewById(R.id.edit_sub_username)
        passwordEdit = findViewById(R.id.edit_sub_password)
        saveButton = findViewById(R.id.btn_sub_save)

        adapter = SubscriptionAdapter(
            onTest = { sub -> testSubscription(sub) },
            onDelete = { sub -> deleteSubscription(sub) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        saveButton.setOnClickListener { saveSubscription() }
    }

    private fun saveSubscription() {
        val name = nameEdit.text.toString().trim().ifBlank { getString(R.string.login_default_name) }
        val server = urlEdit.text.toString().trim()
        val username = usernameEdit.text.toString().trim().takeIf { it.isNotBlank() }
        val password = passwordEdit.text.toString().takeIf { it.isNotBlank() }
        val epgUrl = epgUrlEdit.text.toString().trim().takeIf { it.isNotBlank() }

        if (!server.startsWith("http://", true) && !server.startsWith("https://", true)) {
            urlEdit.error = getString(R.string.login_url_invalid)
            return
        }
        if ((username == null) != (password == null)) {
            Toast.makeText(this, R.string.login_no_credentials, Toast.LENGTH_LONG).show()
            return
        }

        val playlistUrl = if (username != null && password != null &&
            !server.contains("get.php", ignoreCase = true) &&
            !server.contains("m3u", ignoreCase = true)) {
            Uri.parse(server.trimEnd('/') + "/get.php").buildUpon()
                .appendQueryParameter("username", username)
                .appendQueryParameter("password", password)
                .appendQueryParameter("type", "m3u_plus")
                .appendQueryParameter("output", "ts")
                .build().toString()
        } else server

        saveButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val result = subscriptionManager.testSubscription(playlistUrl)
                if (!result.success) {
                    Toast.makeText(
                        this@SubscriptionActivity,
                        getString(R.string.login_connection_failed) + " " + result.message,
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                subscriptionManager.addSubscription(name, playlistUrl, epgUrl, username, password)
                nameEdit.text.clear()
                urlEdit.text.clear()
                epgUrlEdit.text.clear()
                usernameEdit.text.clear()
                passwordEdit.text.clear()
                Toast.makeText(
                    this@SubscriptionActivity,
                    getString(R.string.login_connection_ok, result.channelCount),
                    Toast.LENGTH_LONG
                ).show()
                refreshList()
                setResult(RESULT_OK)
                finish()
            } catch (error: Exception) {
                Toast.makeText(
                    this@SubscriptionActivity,
                    getString(R.string.error_network) + ": " + (error.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressBar.visibility = View.GONE
                saveButton.isEnabled = true
            }
        }
    }

    private fun testSubscription(sub: Subscription) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val result = subscriptionManager.testSubscription(sub.playlistUrl)
                val message = if (result.success) {
                    getString(R.string.sub_test_success) + " (${result.channelCount})"
                } else {
                    getString(R.string.sub_test_fail) + ": ${result.message}"
                }
                Toast.makeText(this@SubscriptionActivity, message, Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(this@SubscriptionActivity, R.string.error_network, Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun deleteSubscription(sub: Subscription) {
        subscriptionManager.deleteSubscription(sub.id)
        refreshList()
    }

    private fun refreshList() {
        val subs = subscriptionManager.getSubscriptions()
        adapter.updateSubscriptions(subs)
        emptyState.visibility = if (subs.isEmpty()) View.VISIBLE else View.GONE
    }
}

/**
 * Subscription list adapter.
 */
class SubscriptionAdapter(
    private val onTest: (Subscription) -> Unit,
    private val onDelete: (Subscription) -> Unit
) : RecyclerView.Adapter<SubscriptionAdapter.SubViewHolder>() {

    private var subscriptions: List<Subscription> = emptyList()

    fun updateSubscriptions(subs: List<Subscription>) {
        subscriptions = subs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subscription, parent, false)
        return SubViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubViewHolder, position: Int) {
        holder.bind(subscriptions[position], onTest, onDelete)
    }

    override fun getItemCount(): Int = subscriptions.size

    class SubViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.sub_item_name)
        private val urlText: TextView = itemView.findViewById(R.id.sub_item_url)
        private val statusText: TextView = itemView.findViewById(R.id.sub_item_status)
        private val btnTest: Button = itemView.findViewById(R.id.sub_item_test)
        private val btnDelete: Button = itemView.findViewById(R.id.sub_item_delete)

        fun bind(
            sub: Subscription,
            onTest: (Subscription) -> Unit,
            onDelete: (Subscription) -> Unit
        ) {
            nameText.text = sub.name
            urlText.text = Uri.parse(sub.playlistUrl).host ?: itemView.context.getString(R.string.login_server_label)
            statusText.text = if (sub.isActive) itemView.context.getString(R.string.sub_active)
            else itemView.context.getString(R.string.sub_inactive)

            btnTest.setOnClickListener { onTest(sub) }
            btnDelete.setOnClickListener { onDelete(sub) }
        }
    }
}
