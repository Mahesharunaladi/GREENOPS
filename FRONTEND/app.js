const DEFAULT_API_URL = 'http://localhost:8080';
const state = {
    apiUrl: localStorage.getItem('greenops-api-url') || DEFAULT_API_URL,
    latestScan: null,
    latestRemediation: null,
    audits: []
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (character) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[character]);
}

function showToast(message, error = false) {
    const toast = $('#toast');
    toast.textContent = message;
    toast.className = `toast show${error ? ' error' : ''}`;
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => { toast.className = 'toast'; }, 4200);
}

async function api(path, options = {}) {
    const response = await fetch(`${state.apiUrl}${path}`, {
        ...options,
        headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.headers || {}) }
    });
    if (!response.ok) {
        const detail = await response.text();
        throw new Error(detail || `Request failed (${response.status})`);
    }
    return response;
}

function formatMoney(value) {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2 }).format(Number(value || 0));
}

function formatDate(value, style = 'medium') {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return new Intl.DateTimeFormat('en-US', { dateStyle: style, timeStyle: 'short' }).format(date);
}

function setLoading(element, loading) {
    if (!element) return;
    element.classList.toggle('loading', loading);
    element.disabled = loading;
}

function goTo(view) {
    const labels = { overview: 'Overview', cost: 'Cost optimization', security: 'Security remediation', audits: 'Audit history', settings: 'Settings' };
    $$('.view').forEach((section) => section.classList.toggle('active', section.id === `view-${view}`));
    $$('.nav-item').forEach((item) => item.classList.toggle('active', item.dataset.view === view));
    $('#breadcrumb-current').textContent = labels[view] || labels.overview;
    $('#sidebar').classList.remove('open');
    $('#menu-button').setAttribute('aria-expanded', 'false');
    if (view === 'audits') loadAudits();
}

function renderScan(result) {
    state.latestScan = result;
    const findings = Array.isArray(result.findings) ? result.findings : [];
    const savings = formatMoney(result.totalEstimatedMonthlySavings);
    const lastScan = result.scannedAt ? new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' }).format(new Date(result.scannedAt)) : 'Completed';

    $('#stat-savings').textContent = savings;
    $('#stat-resources').textContent = result.totalResources ?? '—';
    $('#stat-idle').textContent = result.idleResources ?? '—';
    $('#stat-last-scan').textContent = lastScan;
    $('#stat-scan-mode').textContent = result.dryRun ? 'Dry-run · no changes applied' : 'Live scan';

    $('#cost-resources').textContent = result.totalResources ?? '—';
    $('#cost-idle').textContent = result.idleResources ?? '—';
    $('#cost-savings').textContent = savings;
    $('#cost-scanned-at').textContent = formatDate(result.scannedAt);

    const caption = `${findings.length} finding${findings.length === 1 ? '' : 's'} · scanned ${formatDate(result.scannedAt)}`;
    $('#findings-caption').textContent = caption;
    $('#findings-caption-cost').textContent = caption;
    renderFindings(findings);

    const share = result.totalResources ? Math.max(0, Math.min(100, Number(result.idleResources || 0) / result.totalResources * 100)) : 0;
    $('#overview-scan-summary').innerHTML = `
        <div class="summary-number">${escapeHtml(savings)}<small> estimated / month</small></div>
        <div class="summary-track"><span style="width:${share}%"></span></div>
        <div class="summary-foot"><span>${Number(result.idleResources || 0)} idle candidates</span><span>${Number(result.totalResources || 0)} resources evaluated</span></div>`;
    prependRecentScan(result);
}

function renderFindings(findings) {
    const dashboardBody = $('#findings-body');
    const costBody = $('#cost-findings-body');
    if (!findings.length) {
        const empty = '<tr><td colspan="6"><div class="empty-table"><strong>No idle candidates found</strong><span>The latest scan found no resources matching the configured policy.</span></div></td></tr>';
        dashboardBody.innerHTML = '<tr><td colspan="5"><div class="empty-table"><strong>No idle candidates found</strong><span>The latest scan found no resources matching the configured policy.</span></div></td></tr>';
        costBody.innerHTML = empty;
        return;
    }

    const dashboardRows = [];
    const costRows = findings.map((finding) => {
        const tags = Object.entries(finding.tags || {}).map(([key, value]) => `${key}=${value}`).join(' · ');
        const action = String(finding.action || 'REVIEW').replaceAll('_', ' ');
        const actionStatus = finding.actionTaken ? 'Applied' : (action === 'NONE' ? 'Review' : 'Dry-run');
        const actionClass = finding.actionTaken ? 'done' : 'ready';
        const resource = `<span class="resource-id">${escapeHtml(finding.resourceId)}</span>${tags ? `<br><small>${escapeHtml(tags)}</small>` : ''}`;
        dashboardRows.push(`<tr><td>${resource}</td><td>${escapeHtml(finding.region || '—')}</td><td>${escapeHtml(finding.reason || 'Policy match')}</td><td><strong>${formatMoney(finding.savingsEstimate?.estimatedMonthlySavings)}</strong></td><td><span class="status-chip ${actionClass}">${actionStatus}</span></td></tr>`);
        return `<tr>
            <td>${resource}</td>
            <td>${escapeHtml(finding.region || '—')}</td>
            <td>${escapeHtml(finding.reason || 'Policy match')}</td>
            <td><span class="status-chip warn">${escapeHtml(action)}</span></td>
            <td><strong>${formatMoney(finding.savingsEstimate?.estimatedMonthlySavings)}</strong></td>
            <td><span class="status-chip ${actionClass}">${actionStatus}</span></td>
        </tr>`;
    });
    dashboardBody.innerHTML = dashboardRows.join('');
    costBody.innerHTML = costRows.join('');
}

function prependRecentScan(result) {
    const node = document.createElement('div');
    node.className = 'recent-item';
    node.innerHTML = `<span class="recent-dot"></span><div><strong>Cost scan completed</strong><small>${Number(result.idleResources || 0)} idle candidates · ${formatMoney(result.totalEstimatedMonthlySavings)} estimated monthly</small></div><time>${escapeHtml(new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' }).format(new Date(result.scannedAt || Date.now())))}</time>`;
    const list = $('#recent-activity');
    list.querySelector('.empty-inline')?.remove();
    list.prepend(node);
    while (list.children.length > 4) list.lastElementChild.remove();
}

async function runScan(button) {
    setLoading(button, true);
    showToast('Scanning AWS resource telemetry…');
    try {
        const response = await api('/api/v1/scan?dryRun=true', { method: 'POST' });
        const result = await response.json();
        renderScan(result);
        showToast('Dry-run scan complete. No cloud resources were changed.');
    } catch (error) {
        showToast(`Could not run scan: ${error.message}`, true);
    } finally {
        setLoading(button, false);
    }
}

async function loadAudits({ quiet = false } = {}) {
    const body = $('#audits-body');
    try {
        const response = await api('/api/v1/audits');
        const audits = await response.json();
        if (!Array.isArray(audits)) throw new Error('Unexpected audit response');
        state.audits = audits;
        if (!audits.length) {
            body.innerHTML = '<tr><td colspan="6"><div class="empty-table"><strong>No scan records yet</strong><span>Run a cost scan to create the first audit record.</span></div></td></tr>';
            return audits;
        }
        body.innerHTML = audits.slice().reverse().map((audit) => `<tr>
            <td><strong>${formatDate(audit.scannedAt)}</strong></td>
            <td><span class="status-chip ${audit.dryRun ? 'ready' : 'warn'}">${audit.dryRun ? 'Dry-run' : 'Live'}</span></td>
            <td>${Number(audit.totalResources || 0)}</td>
            <td>${Number(audit.idleResources || 0)}</td>
            <td><strong>${formatMoney(audit.totalEstimatedMonthlySavings)}</strong></td>
            <td>${escapeHtml(audit.slackSummary || '—')}</td>
        </tr>`).join('');
        if (!state.latestScan) renderScan(audits[audits.length - 1]);
        renderRecentAudits(audits);
        return audits;
    } catch (error) {
        if (!quiet) showToast('Audit history is unavailable. Check the backend connection.', true);
        return null;
    }
}

function renderRecentAudits(audits) {
    const list = $('#recent-activity');
    if (!audits.length) return;
    list.innerHTML = audits.slice(-4).reverse().map((audit) => `<div class="recent-item"><span class="recent-dot"></span><div><strong>${audit.dryRun ? 'Dry-run scan completed' : 'Cost scan completed'}</strong><small>${Number(audit.idleResources || 0)} idle candidates · ${formatMoney(audit.totalEstimatedMonthlySavings)} monthly</small></div><time>${escapeHtml(new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' }).format(new Date(audit.scannedAt)))}</time></div>`).join('');
}

function createScanRecord(form) {
    const data = new FormData(form);
    return {
        imageOrYamlName: data.get('imageOrYamlName'),
        scannedAt: new Date().toISOString(),
        criticalCount: Number(data.get('criticalCount') || 0),
        highCount: Number(data.get('highCount') || 0),
        mediumCount: Number(data.get('mediumCount') || 0),
        lowCount: Number(data.get('lowCount') || 0),
        findings: []
    };
}

function createArtifact(form) {
    const data = new FormData(form);
    return { type: data.get('artifactType'), path: data.get('path'), content: data.get('content'), metadata: {} };
}

async function runDryRun(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const button = $('#dry-run-button');
    setLoading(button, true);
    const request = {
        scanRecord: createScanRecord(form),
        artifact: createArtifact(form),
        policies: [{ id: 'no-root', ruleType: 'DISALLOW_ROOT_USER', blocking: true, value: '' }]
    };
    try {
        const response = await api('/api/remediations/dry-run', { method: 'POST', body: JSON.stringify(request) });
        const plan = await response.json();
        state.latestRemediation = { request, plan };
        renderRemediation(plan);
        showToast('Review generated. Inspect the guardrail result and proposed patch.');
    } catch (error) {
        showToast(`Remediation review failed: ${error.message}`, true);
    } finally {
        setLoading(button, false);
    }
}

function renderRemediation(plan) {
    const guardrails = plan.guardrailEvaluation || {};
    const result = $('#remediation-result');
    const vulnerabilities = plan.prioritizedScan?.vulnerabilities || [];
    const violations = guardrails.violations || [];
    result.className = 'result-success';
    result.innerHTML = `<div class="guardrail-result ${guardrails.allowed ? 'allowed' : 'blocked'}"><strong>${guardrails.allowed ? 'Guardrails passed' : 'Guardrails need attention'}</strong><span>${guardrails.allowed ? 'The proposal is eligible for review.' : `${violations.length} policy violation${violations.length === 1 ? '' : 's'} found.`}</span></div>${violations.map((item) => `<div class="finding-mini"><strong>${escapeHtml(item.policyId)}</strong><span>${escapeHtml(item.message)}</span></div>`).join('')}${vulnerabilities.map((item) => `<div class="finding-mini"><strong>${escapeHtml(item.finding?.cveId || 'Finding')} · ${escapeHtml(item.priority || 'REVIEW')}</strong><span>${escapeHtml(item.finding?.packageName || '')} · priority ${Number(item.priorityScore || 0).toFixed(1)}</span></div>`).join('') || '<div class="finding-mini"><strong>Artifact analyzed</strong><span>No vulnerability details were included in this review.</span></div>'}`;
    $('.score-ring').textContent = guardrails.allowed ? 'PASS' : 'CHECK';
    const diff = plan.fixProposal?.unifiedDiff || 'No automated source change was generated for this artifact.';
    $('#diff-content').innerHTML = escapeHtml(diff).replace(/^\+(.*)$/gm, '<span class="diff-added">+$1</span>').replace(/^-(.*)$/gm, '<span class="diff-removed">-$1</span>');
    $('#diff-panel').classList.remove('hidden');
}

async function downloadBinary(path, body, filename) {
    try {
        const response = await api(path, { method: 'POST', body: JSON.stringify(body), headers: { Accept: '*/*' } });
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url; link.download = filename; link.click();
        window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
        showToast(`Download failed: ${error.message}`, true);
    }
}

async function checkConnection(showMessage = true) {
    const status = $('#connection-status');
    const settingsStatus = $('#connection-status-setting');
    const led = $('#service-led');
    const serviceState = $('#service-state');
    const serviceDetail = $('#service-detail');
    status.innerHTML = '<i class="checking"></i>Checking connection';
    if (settingsStatus) settingsStatus.textContent = 'Checking…';
    led.className = 'service-led';
    serviceState.textContent = 'Checking API';
    serviceDetail.textContent = 'Read-only check against the audit endpoint.';
    try {
        const response = await api('/api/v1/audits');
        const records = await response.json();
        if (!Array.isArray(records)) throw new Error('Unexpected response from audit API');
        status.innerHTML = '<i class="connected"></i>Backend connected';
        led.className = 'service-led connected';
        serviceState.textContent = 'Connected';
        serviceDetail.textContent = 'Spring Boot API is responding. Audit history is available.';
        if (settingsStatus) settingsStatus.textContent = 'Connected · audit API responding';
        if (records.length) {
            state.audits = records;
            if (!state.latestScan) renderScan(records[records.length - 1]);
            renderRecentAudits(records);
        }
        if (showMessage) showToast('Backend connection verified.');
    } catch (error) {
        status.innerHTML = '<i class="offline"></i>Backend unavailable';
        led.className = 'service-led offline';
        serviceState.textContent = 'Not connected';
        serviceDetail.textContent = 'Start the backend or update its URL in settings.';
        if (settingsStatus) settingsStatus.textContent = 'Unavailable';
        if (showMessage) showToast('Could not reach the backend. Check the API URL and CORS settings.', true);
    }
}

function bindEvents() {
    $$('.nav-item,[data-view]').forEach((element) => element.addEventListener('click', (event) => {
        event.preventDefault();
        goTo(element.dataset.view);
    }));
    $('#menu-button').addEventListener('click', () => {
        const open = $('#sidebar').classList.toggle('open');
        $('#menu-button').setAttribute('aria-expanded', String(open));
    });
    $('#overview-scan').addEventListener('click', (event) => runScan(event.currentTarget));
    $('#cost-scan').addEventListener('click', (event) => runScan(event.currentTarget));
    $('#refresh-audits').addEventListener('click', () => loadAudits());
    $('#remediation-form').addEventListener('submit', runDryRun);
    $('#finding-search').addEventListener('input', (event) => {
        const query = event.currentTarget.value.toLowerCase();
        $$('#cost-findings-body tr').forEach((row) => { row.style.display = row.textContent.toLowerCase().includes(query) ? '' : 'none'; });
    });
    $('#download-cost-report').addEventListener('click', () => {
        if (!state.latestScan) return showToast('Run a scan before exporting findings.', true);
        const blob = new Blob([JSON.stringify(state.latestScan, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob); const link = document.createElement('a');
        link.href = url; link.download = 'greenops-cost-scan.json'; link.click();
        window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    });
    $('#download-patch').addEventListener('click', () => {
        if (!state.latestRemediation) return showToast('Generate a remediation review first.', true);
        downloadBinary('/api/remediations/patch', state.latestRemediation.request, 'greenops-remediation.patch');
    });
    $('#download-pdf').addEventListener('click', () => {
        if (!state.latestRemediation?.request?.scanRecord) return showToast('Generate a remediation review first.', true);
        downloadBinary('/api/reports/pdf', state.latestRemediation.request.scanRecord, 'greenops-security-report.pdf');
    });
    $('#open-pr').addEventListener('click', async () => {
        if (!state.latestRemediation) return showToast('Generate and review a remediation proposal first.', true);
        if (!state.latestRemediation.plan.guardrailEvaluation?.allowed) return showToast('Blocking guardrails must pass before a pull request can be created.', true);
        const owner = window.prompt('GitHub repository owner');
        const name = window.prompt('GitHub repository name');
        if (!owner || !name) return;
        const branch = `greenops-remediation-${Date.now()}`;
        try {
            const response = await api('/api/remediations/pull-request', {
                method: 'POST',
                body: JSON.stringify({
                    remediation: state.latestRemediation.request,
                    repository: { owner, name, baseBranch: 'main', headBranch: branch }
                })
            });
            const result = await response.json();
            showToast(`Pull request #${result.number} created.`);
            window.open(result.url, '_blank', 'noopener,noreferrer');
        } catch (error) {
            showToast(`Pull request was not created: ${error.message}`, true);
        }
    });
    $('#check-connection').addEventListener('click', () => checkConnection());
    $('#refresh-connection').addEventListener('click', () => checkConnection());
    $('#save-settings').addEventListener('click', () => {
        const value = $('#api-url').value.trim().replace(/\/$/, '');
        if (!/^https?:\/\//i.test(value)) return showToast('Enter a valid HTTP or HTTPS API URL.', true);
        state.apiUrl = value;
        localStorage.setItem('greenops-api-url', value);
        showToast('Backend URL saved. Checking connection…');
        checkConnection(false);
    });
}

bindEvents();
$('#api-url').value = state.apiUrl;
checkConnection(false);
