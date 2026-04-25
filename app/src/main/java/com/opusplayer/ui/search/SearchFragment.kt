package com.opusplayer.ui.search

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.opusplayer.R
import com.opusplayer.databinding.FragmentSearchBinding
import com.opusplayer.model.TrendingItem
import com.opusplayer.service.DownloadService
import com.opusplayer.ui.adapters.TrendingAdapter
import com.opusplayer.utils.gone
import com.opusplayer.utils.sanitizeFilename
import com.opusplayer.utils.visible
import com.opusplayer.viewmodel.SearchViewModel
import java.io.File

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var trendingAdapter: TrendingAdapter

    private var downloadService: DownloadService? = null
    private var isDownloadBound = false

    private val downloadServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            downloadService = (binder as? DownloadService.DownloadBinder)?.getService()
            isDownloadBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            downloadService = null
            isDownloadBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBackNavigation()
        setupWebView()
        setupSearchBar()
        setupTrendingList()
        setupDownloadBar()
        observeViewModel()
        bindDownloadService()
    }

    // -------------------------------------------------------------------------
    // Back navigation — handles WebView back stack smoothly
    // -------------------------------------------------------------------------
    private fun setupBackNavigation() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    // Case 1: WebView visible and has pages to go back to
                    binding.webView.visibility == View.VISIBLE && binding.webView.canGoBack() -> {
                        binding.webView.goBack()
                    }
                    // Case 2: WebView visible but no history — go back to suggestions
                    binding.webView.visibility == View.VISIBLE -> {
                        viewModel.showSuggestions()
                        binding.etSearch.setText("")
                    }
                    // Case 3: Already on suggestions — disable callback and let system handle
                    else -> {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    // -------------------------------------------------------------------------
    // WebView setup
    // -------------------------------------------------------------------------
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
            }

            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.etSearch.setText(url)
                    binding.etSearch.setSelection(url?.length ?: 0)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (isDownloadableUrl(url)) {
                        startDownloadFromUrl(url)
                        return true
                    }
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {}

            setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                val guessed = URLUtil.guessFileName(url, contentDisposition, mimetype)
                val name = if (guessed.endsWith(".mp3", true)) guessed else "$guessed.mp3"
                startDownloadFromUrl(url, name)
            }
        }
    }

    private fun isDownloadableUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") ||
                lower.endsWith(".flac") || lower.endsWith(".ogg") || lower.endsWith(".wav")
    }

    // -------------------------------------------------------------------------
    // Search bar
    // -------------------------------------------------------------------------
    private fun setupSearchBar() {
        binding.etSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                performSearch(); true
            } else false
        }
        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            binding.btnGo.visibility = if (hasFocus) View.VISIBLE else View.GONE
        }
        binding.btnGo.setOnClickListener { performSearch() }
    }

    private fun performSearch() {
        val query = binding.etSearch.text?.toString()?.trim() ?: return
        if (query.isEmpty()) return
        hideKeyboard()
        viewModel.navigateTo(query)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    // -------------------------------------------------------------------------
    // Trending list
    // -------------------------------------------------------------------------
    private fun setupTrendingList() {
        trendingAdapter = TrendingAdapter { item ->
            viewModel.navigateTo("${item.title} ${item.artist} mp3 download")
        }
        binding.rvTrending.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = trendingAdapter
            isNestedScrollingEnabled = false
        }
    }

    // -------------------------------------------------------------------------
    // Download bar
    // -------------------------------------------------------------------------
    private fun setupDownloadBar() {
        binding.btnCancelDownload.setOnClickListener {
            downloadService?.cancelDownload()
            binding.downloadBar.gone()
        }
    }

    // -------------------------------------------------------------------------
    // Observers
    // -------------------------------------------------------------------------
    private fun observeViewModel() {
        viewModel.recentSearches.observe(viewLifecycleOwner) { searches ->
            populateRecentSearches(searches)
        }

        viewModel.trendingItems.observe(viewLifecycleOwner) { items ->
            trendingAdapter.submitList(items)
        }

        viewModel.isWebViewVisible.observe(viewLifecycleOwner) { show ->
            if (show) { binding.webView.visible(); binding.suggestionsPanel.gone() }
            else      { binding.webView.gone();    binding.suggestionsPanel.visible() }
        }

        viewModel.currentUrl.observe(viewLifecycleOwner) { url ->
            if (url != null && binding.webView.url != url) binding.webView.loadUrl(url)
        }

        DownloadService.activeDownload.observe(viewLifecycleOwner) { item ->
            if (item != null) {
                binding.downloadBar.visible()
                binding.tvDownloadName.text = item.title.uppercase()
                binding.tvDownloadPercent.text = "${item.progress}%"
                binding.pbDownload.progress = item.progress
            } else {
                binding.downloadBar.gone()
            }
        }

        DownloadService.downloadComplete.observe(viewLifecycleOwner) { path ->
            if (path != null) {
                Toast.makeText(requireContext(), "Download complete!", Toast.LENGTH_SHORT).show()
                scanNewFile(path)
            }
        }

        DownloadService.downloadError.observe(viewLifecycleOwner) { error ->
            if (error != null)
                Toast.makeText(requireContext(), "Download failed: $error", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // Recent searches chips
    // -------------------------------------------------------------------------
    private fun populateRecentSearches(searches: List<String>) {
        binding.gridRecent.removeAllViews()
        if (searches.isEmpty()) { binding.llRecentSection.gone(); return }
        binding.llRecentSection.visible()
        searches.take(4).forEach { query ->
            binding.gridRecent.addView(createRecentChip(query))
        }
    }

    private fun createRecentChip(query: String): View {
        val params = GridLayout.LayoutParams().apply {
            width = 0
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(4, 4, 4, 4)
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_recent_chip)
            setPadding(16, 20, 16, 20)
            layoutParams = params
        }
        val icon = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_history)
            layoutParams = LinearLayout.LayoutParams(20, 20)
        }
        val text = TextView(requireContext()).apply {
            text = query
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 8 }
        }
        container.addView(icon)
        container.addView(text)
        container.setOnClickListener { viewModel.navigateTo(query) }
        return container
    }

    // -------------------------------------------------------------------------
    // Download helpers
    // -------------------------------------------------------------------------
    fun startDownloadFromUrl(url: String, fileName: String? = null) {
        val name = fileName ?: run {
            val g = URLUtil.guessFileName(url, null, "audio/mpeg")
            if (g.endsWith(".mp3")) g else "$g.mp3"
        }
        val cleanName = name.sanitizeFilename()
        val title = cleanName.removeSuffix(".mp3").replace("_", " ")

        val intent = Intent(requireContext(), DownloadService::class.java).apply {
            putExtra(DownloadService.EXTRA_URL, url)
            putExtra(DownloadService.EXTRA_FILENAME, cleanName)
            putExtra(DownloadService.EXTRA_TITLE, title)
        }
        requireContext().startForegroundService(intent)
        Toast.makeText(requireContext(), "Starting download: $title", Toast.LENGTH_SHORT).show()
    }

    private fun scanNewFile(path: String) {
        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        intent.data = Uri.fromFile(File(path))
        requireContext().sendBroadcast(intent)
    }

    private fun bindDownloadService() {
        requireContext().bindService(
            Intent(requireContext(), DownloadService::class.java),
            downloadServiceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.webView.destroy()
        if (isDownloadBound) {
            requireContext().unbindService(downloadServiceConnection)
            isDownloadBound = false
        }
        _binding = null
    }
}