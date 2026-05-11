const tools = {
    'word-to-pdf': {
        title: 'Word to PDF',
        accept: '.doc,.docx,.odt,.rtf,.txt',
        outputExt: 'pdf',
        isMultiple: true,
        isBatchable: true
    },
    'excel-to-pdf': {
        title: 'Excel to PDF',
        accept: '.xls,.xlsx,.ods,.csv',
        outputExt: 'pdf',
        isMultiple: true,
        isBatchable: true
    },
    'powerpoint-to-pdf': {
        title: 'PowerPoint to PDF',
        accept: '.ppt,.pptx,.odp',
        outputExt: 'pdf',
        isMultiple: true,
        isBatchable: true
    },
    'images-to-pdf': {
        title: 'Images to PDF',
        accept: '.png,.jpg,.jpeg,.gif,.bmp,.webp',
        outputExt: 'pdf',
        isMultiple: true,
        isBatchable: true
    },
    'pdf-to-images': {
        title: 'PDF to Images',
        accept: '.pdf',
        outputExt: 'zip',
        isMultiple: true,
        isBatchable: true
    },
    'pdf-to-word': {
        title: 'PDF to Word',
        accept: '.pdf',
        outputExt: 'docx',
        isMultiple: true,
        isBatchable: true
    },
    'pdf-to-excel': {
        title: 'PDF to Excel',
        accept: '.pdf',
        outputExt: 'xlsx',
        isMultiple: true,
        isBatchable: true
    },
    'pdf-to-ppt': {
        title: 'PDF to PPT',
        accept: '.pdf',
        outputExt: 'pptx',
        isMultiple: true,
        isBatchable: true
    },
    'split-pdf': {
        title: 'Split PDF',
        accept: '.pdf',
        outputExt: 'zip',
        isMultiple: true,
        isBatchable: true
    },
    'merge-pdf': {
        title: 'Merge PDF',
        accept: '.pdf',
        outputExt: 'pdf',
        isMultiple: true
    },
    'compress-pdf': {
        title: 'Compress PDF',
        accept: '.pdf',
        outputExt: 'pdf',
        isMultiple: true,
        isBatchable: true
    },
    'ai-summarize': {
        title: 'AI Summarizer',
        accept: '.pdf',
        outputExt: 'text',
        isAI: true
    }
};

// File size limits (must match application.properties settings)
const MAX_FILE_SIZE = 100 * 1024 * 1024; // 100 MB per file
const MAX_REQUEST_SIZE = 500 * 1024 * 1024; // 500 MB total batch size

let currentTool = '';

function setStatus(message, type = 'info') {
    const statusEl = document.getElementById('status');
    statusEl.innerText = message;
    statusEl.className = 'status ' + type;
}

function getSelectedFile() {
    const fileInput = document.getElementById('fileInput');
    return fileInput.files.length > 0 ? fileInput.files[0] : null;
}

function setTool(toolKey) {
    const config = tools[toolKey];
    if (!config) {
        return;
    }

    currentTool = toolKey;
    const fileInput = document.getElementById('fileInput');
    fileInput.value = '';
    fileInput.setAttribute('accept', config.accept);
    fileInput.multiple = config.isMultiple || false;

    document.getElementById('toolTitle').innerText = config.title;

    let uploadText;
    if (config.isMultiple && config.isBatchable) {
        uploadText = `Upload one or more files for batch conversion\n(${config.accept.replaceAll(',', ', ')})`;
    } else if (config.isMultiple) {
        uploadText = `Upload multiple files (${config.accept.replaceAll(',', ', ')})`;
    } else {
        uploadText = `Upload a file (${config.accept.replaceAll(',', ', ')})`;
    }

    document.getElementById('dropText').innerText = uploadText;
    document.getElementById('toolSelection').classList.add('hidden');
    document.getElementById('uploadSection').classList.remove('hidden');
    document.getElementById('convertBtn').disabled = true;
    document.getElementById('summaryOutput').classList.add('hidden');

    // Show AI Summarizer section if AI tool
    const aiSection = document.getElementById('aiSummarizerSection');
    if (config.isAI) {
        aiSection.classList.remove('hidden');
    } else {
        aiSection.classList.add('hidden');
    }

    setStatus('Select file(s) to start conversion', 'info');
}

function backToTools() {
    currentTool = '';
    document.getElementById('fileInput').value = '';
    document.getElementById('uploadSection').classList.add('hidden');
    document.getElementById('toolSelection').classList.remove('hidden');
    setStatus('', 'info');
}

function setupDragDrop() {
    const dropZone = document.getElementById('dropZone');
    const fileInput = document.getElementById('fileInput');

    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, preventDefaults, false);
    });

    function preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }

    ['dragenter', 'dragover'].forEach(eventName => {
        dropZone.addEventListener(eventName, highlight, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, unhighlight, false);
    });

    function highlight(e) {
        dropZone.classList.add('highlight');
    }

    function unhighlight(e) {
        dropZone.classList.remove('highlight');
    }

    dropZone.addEventListener('drop', handleDrop, false);

    function handleDrop(e) {
        const dt = e.dataTransfer;
        const files = dt.files;
        // Properly set the files on the input element
        fileInput.files = files;
        // Trigger change event to update display
        const changeEvent = new Event('change', { bubbles: true });
        fileInput.dispatchEvent(changeEvent);
    }

    dropZone.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', updateFileDisplay);
}

function updateFileDisplay() {
    const fileInput = document.getElementById('fileInput');
    const files = fileInput.files;
    const convertBtn = document.getElementById('convertBtn');

    if (files.length > 0) {
        // File size validation
        // Check individual file sizes (max 100MB per file)
        for (let i = 0; i < files.length; i++) {
            if (files[i].size > MAX_FILE_SIZE) {
                const sizeMB = (files[i].size / 1024 / 1024).toFixed(2);
                setStatus(`❌ File too large: ${files[i].name} (${sizeMB}MB) exceeds the 100MB limit.`, 'error');
                convertBtn.disabled = true;
                return;
            }
        }

        // Check total batch size (max 500MB combined)
        const totalSize = Array.from(files).reduce((sum, file) => sum + file.size, 0);
        if (totalSize > MAX_REQUEST_SIZE) {
            const totalSizeMB = (totalSize / 1024 / 1024).toFixed(2);
            setStatus(`❌ Batch too large: Total size (${totalSizeMB}MB) exceeds the 500MB limit. Please select fewer files.`, 'error');
            convertBtn.disabled = true;
            return;
        }

        // All validations passed - enable conversion
        let filesInfo;
        if (files.length === 1) {
            filesInfo = `✓ File selected: ${files[0].name} (${(files[0].size / 1024 / 1024).toFixed(2)} MB)`;
        } else {
            filesInfo = `✓ ${files.length} files selected (${(totalSize / 1024 / 1024).toFixed(2)} MB total)`;
        }
        setStatus(filesInfo, 'success');
        convertBtn.disabled = false;
    } else {
        setStatus('Select file(s) to start conversion', 'info');
        convertBtn.disabled = true;
    }
}

window.addEventListener('DOMContentLoaded', () => {
    setupDragDrop();

    // Add click listeners to tool cards instead of buttons
    document.querySelectorAll('.tool-card').forEach((card) => {
        card.addEventListener('click', () => setTool(card.dataset.tool));
    });

    document.getElementById('backBtn').addEventListener('click', backToTools);
    document.getElementById('convertBtn').addEventListener('click', convertFile);
    document.getElementById('copySummaryBtn').addEventListener('click', copySummaryToClipboard);

    // Tool links (dropdowns/footer)
    document.querySelectorAll('.tool-link').forEach((link) => {
        link.addEventListener('click', (e) => {
            const toolKey = link.dataset.tool;
            if (toolKey) {
                setToolAndScroll(toolKey, e);
            }
        });
    });

    // Mobile hamburger menu toggle
    const navToggle = document.querySelector('.nav-toggle');
    const nav = document.querySelector('nav');
    const navLinks = document.querySelector('.nav-links');

    if (navToggle) {
        navToggle.addEventListener('click', () => {
            const isOpen = nav.classList.toggle('nav-open');
            // Toggle icon between hamburger (☰) and close (✕)
            navToggle.textContent = isOpen ? '✕' : '☰';
        });

        // Close menu when a non-dropdown link is clicked
        document.querySelectorAll('.nav-links > li > a:not(.nav-link-dropdown)').forEach((link) => {
            link.addEventListener('click', () => {
                nav.classList.remove('nav-open');
                navToggle.textContent = '☰';
            });
        });

        // Close menu when a dropdown menu item is clicked
        document.querySelectorAll('.dropdown-menu a').forEach((link) => {
            link.addEventListener('click', () => {
                nav.classList.remove('nav-open');
                navToggle.textContent = '☰';
            });
        });
    }

    // Mobile dropdown menu handling
    setupMobileDropdowns();
});

/**
 * Sets the tool and scrolls to the upload section
 * @param {string} toolKey - The tool key
 */
function setToolAndScroll(toolKey, triggerEvent) {
    if (triggerEvent) {
        triggerEvent.preventDefault();
    }
    setTool(toolKey);
    
    // Scroll to the upload section smoothly
    setTimeout(() => {
        const uploadSection = document.getElementById('uploadSection');
        if (uploadSection) {
            uploadSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }, 100);
}

/**
 * Setup mobile dropdown interactions
 */
function setupMobileDropdowns() {
    const dropdownItems = document.querySelectorAll('.nav-item-dropdown');
    const navLinks = document.querySelector('.nav-links');
    
    dropdownItems.forEach((item) => {
        const link = item.querySelector('.nav-link-dropdown');
        
        // Handle click on dropdown toggle
        link.addEventListener('click', (e) => {
            e.preventDefault();
            // Only toggle on mobile (screen width <= 768px AND nav is open)
            const isMobile = window.innerWidth <= 768;
            const isNavOpen = document.querySelector('nav').classList.contains('nav-open');
            
            if (isMobile && isNavOpen) {
                const isActive = item.classList.toggle('active');
                
                // Close other dropdowns when opening one
                if (isActive) {
                    dropdownItems.forEach((otherItem) => {
                        if (otherItem !== item) {
                            otherItem.classList.remove('active');
                        }
                    });
                }
            }
        });

        // Close dropdown when a menu item is clicked
        const menuItems = item.querySelectorAll('.dropdown-menu a');
        menuItems.forEach((menuItem) => {
            menuItem.addEventListener('click', () => {
                item.classList.remove('active');
            });
        });
    });
}

async function convertFile() {
    const fileInput = document.getElementById('fileInput');
    const files = fileInput.files;

    if (!currentTool) {
        setStatus('Please choose a conversion tool first.', 'error');
        return;
    }

    if (files.length === 0) {
        setStatus('Please select file(s) first.', 'error');
        return;
    }

    const convertBtn = document.getElementById('convertBtn');
    convertBtn.disabled = true;

    try {
        let endpoint = `/api/convert/${currentTool}`;
        let formData = new FormData();
        const config = tools[currentTool];
        let isBatch = false;

        if (currentTool === 'merge-pdf') {
            endpoint = `/api/convert/merge-pdf`;
            for (let file of files) {
                formData.append('files', file);
            }
            setStatus(`Merging ${files.length} PDF files...`, 'info');
        } else if (currentTool === 'ai-summarize') {
            // AI Summarizer - async task flow
            endpoint = `/api/ai/summarize`;
            const summaryLength = document.getElementById('summaryLength').value;
            formData.append('file', files[0]);
            formData.append('length', summaryLength);
            setStatus(`Summarizing PDF with AI...`, 'info');

            const res = await fetch(endpoint, {
                method: 'POST',
                body: formData
            });

            if (!res.ok) {
                const errorText = await res.text();
                setStatus(errorText || `Summarization failed (HTTP ${res.status}).`, 'error');
                convertBtn.disabled = false;
                return;
            }

            const responseData = await res.json();
            const taskId = responseData.taskId;
            if (!taskId) {
                setStatus('Error: No task ID received from server.', 'error');
                convertBtn.disabled = false;
                return;
            }

            pollSummaryTaskStatus(taskId, convertBtn);
            return;
        } else if (config.isBatchable && files.length > 1) {
            // Batch conversion for multiple files
            endpoint = `/api/convert/batch/${currentTool}`;
            for (let file of files) {
                formData.append('files', file);
            }
            isBatch = true;
            setStatus(`Converting ${files.length} files in batch...`, 'info');
        } else {
            // Single file conversion
            const file = files[0];
            formData.append('file', file);
            setStatus(`Converting ${file.name}...`, 'info');
        }

        // Initiate async conversion
        const res = await fetch(endpoint, {
            method: 'POST',
            body: formData
        });

        if (!res.ok) {
            const errorText = await res.text();
            setStatus(errorText || `Conversion failed (HTTP ${res.status}).`, 'error');
            convertBtn.disabled = false;
            return;
        }

        // Extract taskId from response
        const responseData = await res.json();
        const taskId = responseData.taskId;

        if (!taskId) {
            setStatus('Error: No task ID received from server.', 'error');
            convertBtn.disabled = false;
            return;
        }

        // Start polling for task completion
        const originalFileName = isBatch ?
            `batch_conversion_${new Date().getTime()}.zip` :
            files[0].name.replace(/\.[^/.]+$/, '') + '.' + config.outputExt;

        pollTaskStatus(taskId, config, originalFileName, isBatch, convertBtn, files.length);

    } catch (e) {
        setStatus('Error connecting to server: ' + e.message, 'error');
        convertBtn.disabled = false;
    }
}

/**
 * Polls the conversion task status every 2 seconds until completion.
 * @param {string} taskId - The unique task ID
 * @param {object} config - The tool configuration
 * @param {string} originalFileName - The original filename for download
 * @param {boolean} isBatch - Whether this is a batch conversion
 * @param {HTMLElement} convertBtn - The convert button element
 * @param {number} fileCount - Number of files being converted
 */
function pollTaskStatus(taskId, config, originalFileName, isBatch, convertBtn, fileCount) {
    let pollCount = 0;
    const maxPolls = 1800; // 1 hour max (1800 * 2 seconds)
    const pollIntervalMs = 2000;

    // Show progress bar when polling starts
    const progressBar = document.getElementById('progressBar');
    progressBar.classList.remove('hidden');

    const pollInterval = setInterval(async () => {
        pollCount++;

        try {
            const res = await fetch(`/api/convert/status/${taskId}`);

            if (!res.ok) {
                if (res.status === 404) {
                    clearInterval(pollInterval);
                    progressBar.classList.add('hidden');
                    setStatus('Error: Task not found.', 'error');
                    convertBtn.disabled = false;
                }
                return;
            }

            const taskStatus = await res.json();
            const status = taskStatus.status;

            if (status === 'PROCESSING' || status === 'PENDING') {
                // Still processing
                const elapsedSeconds = Math.round((pollCount * pollIntervalMs) / 1000);
                setStatus(`Processing... please wait (${elapsedSeconds} seconds elapsed)`, 'info');
            } else if (status === 'FAILED') {
                // Conversion failed - hide progress bar
                clearInterval(pollInterval);
                progressBar.classList.add('hidden');
                const errorMsg = taskStatus.errorMessage || 'Unknown error occurred';
                setStatus(`✗ Conversion failed: ${errorMsg}`, 'error');
                convertBtn.disabled = false;
            } else if (status === 'COMPLETED') {
                // Conversion complete - hide progress bar and trigger download
                clearInterval(pollInterval);
                progressBar.classList.add('hidden');
                downloadConvertedFile(taskId, originalFileName, isBatch, fileCount, convertBtn, config);
            }

            // Safety check: Stop polling if exceeded max attempts
            if (pollCount >= maxPolls) {
                clearInterval(pollInterval);
                progressBar.classList.add('hidden');
                setStatus('Error: Conversion exceeded maximum time limit.', 'error');
                convertBtn.disabled = false;
            }

        } catch (e) {
            clearInterval(pollInterval);
            progressBar.classList.add('hidden');
            setStatus('Error polling task status: ' + e.message, 'error');
            convertBtn.disabled = false;
        }
    }, pollIntervalMs); // Poll every 2 seconds
}

/**
 * Polls the AI summarization task status every 2 seconds until completion.
 * @param {string} taskId - The unique task ID
 * @param {HTMLElement} convertBtn - The convert button element
 */
function pollSummaryTaskStatus(taskId, convertBtn) {
    let pollCount = 0;
    const maxPolls = 1800; // 1 hour max
    const pollIntervalMs = 2000;

    const progressBar = document.getElementById('progressBar');
    progressBar.classList.remove('hidden');

    const pollInterval = setInterval(async () => {
        pollCount++;

        try {
            const res = await fetch(`/api/ai/status/${taskId}`);

            if (!res.ok) {
                if (res.status === 404) {
                    clearInterval(pollInterval);
                    progressBar.classList.add('hidden');
                    setStatus('Error: Task not found.', 'error');
                    convertBtn.disabled = false;
                }
                return;
            }

            const taskStatus = await res.json();
            const status = taskStatus.status;

            if (status === 'PROCESSING' || status === 'PENDING') {
                const elapsedSeconds = Math.round((pollCount * pollIntervalMs) / 1000);
                setStatus(`Summarizing... please wait (${elapsedSeconds} seconds elapsed)`, 'info');
            } else if (status === 'FAILED') {
                clearInterval(pollInterval);
                progressBar.classList.add('hidden');
                const errorMsg = taskStatus.errorMessage || 'Unknown error occurred';
                setStatus(`✗ Summarization failed: ${errorMsg}`, 'error');
                convertBtn.disabled = false;
            } else if (status === 'COMPLETED') {
                clearInterval(pollInterval);
                progressBar.classList.add('hidden');
                fetchSummaryResult(taskId, convertBtn);
            }

            if (pollCount >= maxPolls) {
                clearInterval(pollInterval);
                progressBar.classList.add('hidden');
                setStatus('Error: Summarization exceeded maximum time limit.', 'error');
                convertBtn.disabled = false;
            }
        } catch (e) {
            clearInterval(pollInterval);
            progressBar.classList.add('hidden');
            setStatus('Error polling task status: ' + e.message, 'error');
            convertBtn.disabled = false;
        }
    }, pollIntervalMs);
}

/**
 * Fetches the completed AI summary result.
 * @param {string} taskId - The unique task ID
 * @param {HTMLElement} convertBtn - The convert button element
 */
async function fetchSummaryResult(taskId, convertBtn) {
    try {
        const res = await fetch(`/api/ai/result/${taskId}`);
        if (!res.ok) {
            const errorData = await res.json();
            setStatus(`✗ Summary retrieval failed: ${errorData.errorMessage || 'Unknown error'}`, 'error');
            convertBtn.disabled = false;
            return;
        }

        const result = await res.json();
        displaySummaryResult(result);
        convertBtn.disabled = false;
    } catch (e) {
        setStatus('Error retrieving summary: ' + e.message, 'error');
        convertBtn.disabled = false;
    }
}

/**
 * Downloads the converted file after task completion.
 * @param {string} taskId - The unique task ID
 * @param {string} originalFileName - The filename for download
 * @param {boolean} isBatch - Whether this is a batch conversion
 * @param {number} fileCount - Number of files converted
 * @param {HTMLElement} convertBtn - The convert button element
 * @param {object} config - The tool configuration
 */
async function downloadConvertedFile(taskId, originalFileName, isBatch, fileCount, convertBtn, config) {
    try {
        setStatus('Downloading converted file...', 'info');

        const res = await fetch(`/api/convert/download/${taskId}`);

        if (!res.ok) {
            const errorData = await res.json();
            setStatus(`✗ Download failed: ${errorData.errorMessage || 'Unknown error'}`, 'error');
            convertBtn.disabled = false;
            return;
        }

        // Get the file blob
        const blob = await res.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = originalFileName;

        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);

        // Update status
        if (isBatch) {
            setStatus(`✓ Batch conversion successful: ${originalFileName}. Downloaded zip contains ${fileCount} folders.`, 'success');
        } else {
            setStatus(`✓ Conversion successful: ${originalFileName}`, 'success');
        }

        convertBtn.disabled = false;

    } catch (e) {
        setStatus('Error downloading file: ' + e.message, 'error');
        convertBtn.disabled = false;
    }
}


function displaySummaryResult(result) {
    const summaryOutput = document.getElementById('summaryOutput');
    const summaryStats = document.getElementById('summaryStats');
    const summaryText = document.getElementById('summaryText');

    summaryStats.innerText = `Original: ${result.originalLength} | Summary: ${result.summaryLength}`;
    summaryText.innerText = result.summary;
    summaryOutput.classList.remove('hidden');

    setStatus('✓ Summary generated successfully!', 'success');
}

function copySummaryToClipboard() {
    const summaryText = document.getElementById('summaryText').innerText;
    navigator.clipboard.writeText(summaryText).then(() => {
        const btn = document.getElementById('copySummaryBtn');
        const originalText = btn.innerText;
        btn.innerText = '✓ Copied!';
        setTimeout(() => {
            btn.innerText = originalText;
        }, 2000);
    }).catch(err => {
        setStatus('Failed to copy summary: ' + err.message, 'error');
    });
}
