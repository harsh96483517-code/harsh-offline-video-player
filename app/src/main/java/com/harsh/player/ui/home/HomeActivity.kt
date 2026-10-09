package com.harsh.player.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.os.Environment
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.harsh.player.R
import com.harsh.player.data.VideoRepository
import com.harsh.player.data.model.LibraryCategory
import com.harsh.player.data.model.VideoItem
import com.harsh.player.databinding.ActivityHomeBinding
import com.harsh.player.ui.about.AboutActivity
import com.harsh.player.ui.library.LibraryActivity
import com.harsh.player.ui.player.PlayerActivity
import com.harsh.player.ui.settings.SettingsActivity
import com.harsh.player.util.DeviceCapabilities
import com.harsh.player.util.Formatters
import kotlinx.coroutines.launch

/**
 * HARSH home screen. Sidebar + shelves of local videos. Everything here is
 * remote-first: focus moves predictably and never disappears.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityHomeBinding
    private val vm: HomeViewModel by viewModels()
    private lateinit var repo: VideoRepository

    private val shelfAdapter by lazy { ShelfAdapter(repo) { openPlayer(it) } }
    private val sidebarAdapter by lazy { SidebarAdapter { onSidebar(it) } }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) vm.refresh() else showPermissionRationale()
        }

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                lifecycleScopeLaunch {
                    repo.addSafFolder(it)
                    vm.refresh()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)
        repo = VideoRepository.get(this)

        setupSidebar()
        setupShelves()
        setupTopBar()
        showStorageInfo()

        lifecycleScope.launch {
            vm.state.collect { state ->
                b.progress.visibility = if (state.loading) View.VISIBLE else View.GONE
                shelfAdapter.submit(state.shelves)
                b.emptyState.visibility = if (!state.loading && state.empty) View.VISIBLE else View.GONE
            }
        }

        ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        // Refresh lightweight views (favorites, resume progress) on return.
        vm.refresh()
    }

    private fun setupSidebar() {
        b.sidebar.layoutManager = LinearLayoutManager(this)
        b.sidebar.adapter = sidebarAdapter
        b.sidebar.itemAnimator = null
    }

    private fun setupShelves() {
        b.shelves.layoutManager = LinearLayoutManager(this)
        b.shelves.adapter = shelfAdapter
        b.shelves.setHasFixedSize(true)
    }

    private fun setupTopBar() {
        b.btnSearch.setOnClickListener { showSearch() }
        b.btnFolder.setOnClickListener { folderPicker.launch(null) }
        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        b.offlineBadge.text = getString(R.string.offline_mode)
    }

    private fun showStorageInfo() {
        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val free = stat.availableBytes
            val total = stat.totalBytes
            b.storageInfo.text = getString(
                R.string.storage_info,
                Formatters.fileSize(free), Formatters.fileSize(total)
            )
        }.onFailure { b.storageInfo.visibility = View.GONE }
    }

    private fun onSidebar(category: LibraryCategory) {
        sidebarAdapter.select(category)
        when (category) {
            LibraryCategory.HOME -> Unit // already here
            LibraryCategory.SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            LibraryCategory.ABOUT -> startActivity(Intent(this, AboutActivity::class.java))
            else -> {
                val intent = Intent(this, LibraryActivity::class.java)
                intent.putExtra(LibraryActivity.EXTRA_CATEGORY, category.name)
                startActivity(intent)
            }
        }
    }

    private fun openPlayer(item: VideoItem) {
        startActivity(PlayerActivity.intent(this, item))
    }

    private fun showSearch() {
        val input = EditText(this)
        input.hint = getString(R.string.search_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.search_title)
            .setView(input)
            .setPositiveButton(R.string.action_search) { _, _ ->
                val q = input.text.toString()
                if (q.isNotBlank()) {
                    val intent = Intent(this, LibraryActivity::class.java)
                    intent.putExtra(LibraryActivity.EXTRA_CATEGORY, LibraryCategory.ALL_VIDEOS.name)
                    intent.putExtra(LibraryActivity.EXTRA_QUERY, q)
                    startActivity(intent)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun ensurePermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        val missing = needed.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) vm.refresh() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle(R.string.perm_title)
            .setMessage(R.string.perm_message)
            .setPositiveButton(R.string.action_open_settings) { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
            }
            .setNegativeButton(R.string.action_later, null)
            .show()
    }

    /** Small helper so we can call suspend code from activity callbacks. */
    private fun lifecycleScopeLaunch(block: suspend () -> Unit) {
        lifecycleScope.launch { block() }
    }
}
