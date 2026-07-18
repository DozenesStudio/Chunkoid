function marked(markdown) {
    let html = markdown;

    html = html.replace(/^\uFEFF/, '');

    html = parseTables(html);

    html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/gim, '<a href="$2" target="_blank" rel="noopener">$1</a>');

    html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/gim, '<img src="$2" alt="$1" class="md-img">');

    html = html.replace(/^### (.*$)/gim, '<h3 class="md-h3">$1</h3>');
    html = html.replace(/^## (.*$)/gim, '<h2 class="md-h2">$1</h2>');
    html = html.replace(/^# (.*$)/gim, '<h1 class="md-h1">$1</h1>');

    html = html.replace(/\*\*\*(.+?)\*\*\*/gim, '<strong><em>$1</em></strong>');
    html = html.replace(/\*\*(.+?)\*\*/gim, '<strong>$1</strong>');
    html = html.replace(/\*(.+?)\*/gim, '<em>$1</em>');

    html = html.replace(/`([^`]+)`/gim, '<code class="md-code">$1</code>');

    html = html.replace(/```(\w*)\n([\s\S]*?)```/gim, function(match, lang, code) {
        return '<pre class="md-pre"><code class="md-code-block">' + escapeHtml(code.trim()) + '</code></pre>';
    });

    html = html.replace(/^\d+\.\s+(.*$)/gim, '<li class="md-ol-item">$1</li>');

    html = html.replace(/^\s*[-*+]\s+(.*$)/gim, '<li class="md-ul-item">$1</li>');

    html = html.replace(/<li class="md-ol-item">[\s\S]*?(?=\n\n|\n<li class="md-ul-item">|$)/gim, function(match) {
        return '<ol class="md-ol">' + match + '</ol>';
    });

    html = html.replace(/<li class="md-ul-item">[\s\S]*?(?=\n\n|\n<li class="md-ol-item">|$)/gim, function(match) {
        return '<ul class="md-ul">' + match + '</ul>';
    });

    html = html.replace(/^\s*<hr\s*\/?>\s*$/gim, '<hr class="md-hr">');
    html = html.replace(/^-{3,}$/gim, '<hr class="md-hr">');

    html = html.replace(/^(?!<(h[1-3]|li|tr|pre|table|ul|ol|td|th|thead|tbody|\/))(.+)$/gim, function(match, p1, p2) {
        if (p2 && p2.trim()) {
            return '<p class="md-p">' + p2 + '</p>';
        }
        return match;
    });

    html = html.replace(/<\/(ul|ol|table|pre)>\s*<p class="md-p">/gim, '</$1>\n<p class="md-p">');
    html = html.replace(/<p class="md-p">\s*<(ul|ol|table|pre)/gim, '<$1');

    html = html.replace(/<\/li>\s*<li/gim, '</li>\n<li');
    html = html.replace(/<\/tr>\s*<tr/gim, '</tr>\n<tr');

    html = html.replace(/\n\n/gim, '</p>\n\n<p class="md-p">');
    html = html.replace(/<p class="md-p">\s*<\/p>/gim, '');

    html = html.replace(/^<p class="md-p">\s*$/gm, '');
    html = html.replace(/<p class="md-p">\s*<p class="md-p">/gim, '<p class="md-p">');

    html = html.replace(/<li class="md-ol-item">/gim, '<li>');
    html = html.replace(/<li class="md-ul-item">/gim, '<li>');

    return html.trim();
}

function parseTables(text) {
    const lines = text.split('\n');
    const result = [];
    let i = 0;

    while (i < lines.length) {
        const line = lines[i];
        const trimmed = line.trim();

        if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
            const tableLines = [line];
            i++;

            while (i < lines.length) {
                const nextLine = lines[i];
                const nextTrimmed = nextLine.trim();
                
                if (nextTrimmed.startsWith('|') && nextTrimmed.endsWith('|')) {
                    tableLines.push(nextLine);
                    i++;
                } else {
                    break;
                }
            }

            const tableHtml = convertTableToHtml(tableLines);
            result.push(tableHtml);
            continue;
        }

        result.push(line);
        i++;
    }

    return result.join('\n');
}

function convertTableToHtml(tableLines) {
    if (tableLines.length < 2) {
        return tableLines.join('\n');
    }

    const headers = parseTableRow(tableLines[0]);
    if (!headers || headers.length === 0) {
        return tableLines.join('\n');
    }

    let dataLines = tableLines.slice(1);

    if (dataLines.length > 0 && isSeparatorLine(dataLines[0])) {
        dataLines = dataLines.slice(1);
    }

    let theadHtml = '<thead><tr>';
    headers.forEach(header => {
        theadHtml += '<th>' + header + '</th>';
    });
    theadHtml += '</tr></thead>';

    let tbodyHtml = '<tbody>';
    dataLines.forEach(line => {
        const cells = parseTableRow(line);
        if (cells && cells.length > 0) {
            tbodyHtml += '<tr>';
            cells.forEach(cell => {
                tbodyHtml += '<td>' + cell + '</td>';
            });
            tbodyHtml += '</tr>';
        }
    });
    tbodyHtml += '</tbody>';

    return '<table class="md-table">' + theadHtml + tbodyHtml + '</table>';
}

function parseTableRow(line) {
    const trimmed = line.trim();
    if (!trimmed.startsWith('|') || !trimmed.endsWith('|')) {
        return null;
    }

    const content = trimmed.slice(1, -1);
    const cells = content.split('|').map(cell => cell.trim());
    
    return cells.length > 0 ? cells : null;
}

function isSeparatorLine(line) {
    const trimmed = line.trim();
    if (!trimmed.startsWith('|') || !trimmed.endsWith('|')) {
        return false;
    }

    const content = trimmed.slice(1, -1);
    const cells = content.split('|');
    
    return cells.every(cell => cell.trim().match(/^[:\- ]+$/));
}

function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.replace(/[&<>"']/g, function(m) { return map[m]; });
}