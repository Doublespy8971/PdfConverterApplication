const tools = {
    'word-to-pdf': {
        title: 'Word to PDF',
        accept: '.doc,.docx,.odt,.rtf,.txt',
        outputExt: 'pdf'
    },
    'excel-to-pdf': {
        title: 'Excel to PDF',
        accept: '.xls,.xlsx,.ods,.csv',
        outputExt: 'pdf'
    },
    'powerpoint-to-pdf': {
        title: 'PowerPoint to PDF',
        accept: '.ppt,.pptx,.odp',
        outputExt: 'pdf'
    },
    'images-to-pdf': {
        title: 'Images to PDF',
        accept: '.png,.jpg,.jpeg,.gif,.bmp,.webp',
        outputExt: 'pdf'
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

    document.querySelectorAll('.tool-btn').forEach((button) => {
        button.addEventListener('click', () => setTool(button.dataset.tool));
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

        if (currentTool === 'merge-pdf') {
            endpoint = `/api/convert/merge-pdf`;
            for (let file of files) {
                formData.append('files', file);
            }
            setStatus(`Merging ${files.length} PDF files...`, 'info');
        } else if (currentTool === 'ai-summarize') {
            endpoint = `/api/ai/summarize`;
            const summaryLength = document.getElementById('summaryLength').value;
            formData.append('file', files[0]);
            formData.append('length', summaryLength);
            setStatus(`Summarizing PDF with AI...`, 'info');
        } else if (config.isBatchable && files.length > 1) {
            // Batch conversion for multiple files
            endpoint = `/api/convert/batch/${currentTool}`;
            for (let file of files) {
                formData.append('files', file);
            }
            setStatus(`Converting ${files.length} files in batch...`, 'info');
        } else {
            // Single file conversion
            const file = files[0];
            formData.append('file', file);
            setStatus(`Converting ${file.name}...`, 'info');
        }

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

        if (currentTool === 'ai-summarize') {
            const result = await res.json();
            displaySummaryResult(result);
        } else {
            const blob = await res.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;

            // Generate appropriate filename
            if (currentTool === 'merge-pdf') {
                a.download = `merged.pdf`;
            } else if (config.isBatchable && files.length > 1) {
                a.download = `batch_conversion_${new Date().getTime()}.zip`;
            } else {
                a.download = `${files[0].name.replace(/\.[^/.]+$/, '')}.${config.outputExt}`;
            }

            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);

            if (config.isBatchable && files.length > 1) {
                setStatus(`✓ Batch conversion successful: ${a.download}. Downloaded zip contains ${files.length} folders.`, 'success');
            } else {
                setStatus(`✓ Conversion successful: ${a.download}`, 'success');
            }
        }

        convertBtn.disabled = false;
    } catch (e) {
        setStatus('Error connecting to server: ' + e.message, 'error');
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

