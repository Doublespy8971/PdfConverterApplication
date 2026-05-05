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
        let filesInfo;
        if (files.length === 1) {
            filesInfo = `File selected: ${files[0].name} (${(files[0].size / 1024 / 1024).toFixed(2)} MB)`;
        } else {
            const totalSize = Array.from(files).reduce((sum, file) => sum + file.size, 0);
            filesInfo = `${files.length} files selected (${(totalSize / 1024 / 1024).toFixed(2)} MB total)`;
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
});

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
            // AI Summarizer - Keep synchronous logic
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

            const result = await res.json();
            displaySummaryResult(result);
            convertBtn.disabled = false;
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

    const pollInterval = setInterval(async () => {
        pollCount++;

        try {
            const res = await fetch(`/api/convert/status/${taskId}`);

            if (!res.ok) {
                if (res.status === 404) {
                    clearInterval(pollInterval);
                    setStatus('Error: Task not found.', 'error');
                    convertBtn.disabled = false;
                }
                return;
            }

            const taskStatus = await res.json();
            const status = taskStatus.status;

            if (status === 'PROCESSING' || status === 'PENDING') {
                // Still processing
                setStatus(`Processing... please wait (${pollCount * 2} seconds elapsed)`, 'info');
            } else if (status === 'FAILED') {
                // Conversion failed
                clearInterval(pollInterval);
                const errorMsg = taskStatus.errorMessage || 'Unknown error occurred';
                setStatus(`✗ Conversion failed: ${errorMsg}`, 'error');
                convertBtn.disabled = false;
            } else if (status === 'COMPLETED') {
                // Conversion complete - trigger download
                clearInterval(pollInterval);
                downloadConvertedFile(taskId, originalFileName, isBatch, fileCount, convertBtn, config);
            }

            // Safety check: Stop polling if exceeded max attempts
            if (pollCount >= maxPolls) {
                clearInterval(pollInterval);
                setStatus('Error: Conversion exceeded maximum time limit.', 'error');
                convertBtn.disabled = false;
            }

        } catch (e) {
            clearInterval(pollInterval);
            setStatus('Error polling task status: ' + e.message, 'error');
            convertBtn.disabled = false;
        }
    }, 2000); // Poll every 2 seconds
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

