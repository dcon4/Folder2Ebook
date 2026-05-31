package com.folder2ebook.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.folder2ebook.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val fileAdapter = FileListAdapter()

    // Folder picker using Storage Access Framework
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.onFolderSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeState()
    }

    private fun setupUI() {
        // RecyclerView
        binding.rvFiles.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = fileAdapter
        }

        // Select Folder button
        binding.btnSelectFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        // Book title input
        binding.etBookTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.onBookTitleChanged(s?.toString() ?: "")
            }
        })

        // Author input
        binding.etAuthor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.onAuthorChanged(s?.toString() ?: "")
            }
        })

        // Generate button
        binding.btnGenerate.setOnClickListener {
            viewModel.generateEpub()
        }

        // Share button
        binding.btnShare.setOnClickListener {
            shareEpub()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // Update folder info
                if (state.folderName.isNotEmpty()) {
                    binding.tvFolderName.text = state.folderName
                    binding.tvFolderName.visibility = View.VISIBLE
                    binding.tvFileCount.text = "${state.files.size} supported files found"
                    binding.tvFileCount.visibility = View.VISIBLE
                } else {
                    binding.tvFolderName.visibility = View.GONE
                    binding.tvFileCount.visibility = View.GONE
                }

                // Update file list
                fileAdapter.submitList(state.files)

                // Update title field (only if user hasn't edited it)
                if (binding.etBookTitle.text.toString() != state.bookTitle &&
                    !binding.etBookTitle.hasFocus()) {
                    binding.etBookTitle.setText(state.bookTitle)
                }

                // Show/hide generate section
                binding.layoutGenerateSection.visibility =
                    if (state.files.isNotEmpty()) View.VISIBLE else View.GONE

                // Progress
                binding.progressBar.visibility =
                    if (state.isGenerating) View.VISIBLE else View.GONE
                binding.tvProgress.text = state.progress
                binding.tvProgress.visibility =
                    if (state.progress.isNotEmpty()) View.VISIBLE else View.GONE

                // Generate button state
                binding.btnGenerate.isEnabled = !state.isGenerating && state.files.isNotEmpty()

                // Share button
                binding.btnShare.visibility =
                    if (state.generatedFile != null) View.VISIBLE else View.GONE

                // Error
                if (state.error != null) {
                    Toast.makeText(this@MainActivity, state.error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareEpub() {
        val file = viewModel.uiState.value.generatedFile ?: return
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/epub+zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, viewModel.uiState.value.bookTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share EPUB"))
    }
}
