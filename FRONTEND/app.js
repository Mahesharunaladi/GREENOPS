const state = { apiUrl: localStorage.getItem('greenops-api-url') || 'http://localhost:8080', latestScan: null, latestRemediation: null };
const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

function showToast(message, error = false) {
const toast = $('#toast'); toast.textContent = message; toast.className = `toast show${error ? ' error' : ''}`;
window.clearTimeout(showToast.timer); showToast.timer = window.setTimeout(() => { toast.className = 'toast'; }, 4200);
}
function api(path, options = {}) {
return fetch(`${state.apiUrl}${path}`, { headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }, ...options })
    .then(async response => { if (!response.ok) { const body = await response.text(); throw new Error(body || `Request failed (${response.status})`); } return response; });
}
function formatMoney(value) { return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value || 0)); }
function formatDate(value) { if (!value) return '—'; return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)); }
function escapeHtml(value) { return String(value ?? '').replace(/[&<>'"]/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' }[c])); }
function setLoading(element, loading) { element?.classList.toggle('loading', loading); }

function goTo(view) {
$$('.view').forEach(section => section.classList.toggle('active', section.id === `view-${view}`));
$$('.nav-item').forEach(item => item.classList.toggle('active', item.dataset.view === view));
$('#breadcrumb-current').textContent = view === 'overview' ? 'Overview' : view[0].toUpperCase() + view.slice(1);
$('#sidebar').classList.remove('open');
if (view === 'audits') loadAudits();
}

function renderScan(result) {
state.latestScan = result;
const savings = formatMoney(result.totalEstimatedMonthlySavings);
['stat-savings', 'cost-savings'].forEach(id => { const node = $(`#${id}`); if (node) node.textContent = savings; });
['stat-resources', 'cost-resources'].forEach(id => { const node = $(`#${id}`); if (node) node.textContent = result.totalResources ?? '—'; });
['stat-idle', 'cost-idle'].forEach(id => { const node = $(`#${id}`); if (node) node.textContent = result.idleResources ?? '—'; });
$('#findings-caption').textContent = `${result.findings?.length || 0} idle resource${result.findings?.length === 1 ? '' : 's'} found · ${formatDate(result.scannedAt)}`;
renderFindings(result.findings || []);
$('#recent-activity').innerHTML = `<div class="activity-item"><span class="activity-dot"></span><div><strong>Cost scan completed</strong><small>${result.idleResources || 0} idle resources · ${formatMoney(result.totalEstimatedMonthlySavings)} opportunity · ${formatDate(result.scannedAt)}</small></div></div>${(result.findings || []).slice(0, 2).map(f => `<div class="activity-item"><span class="activity-dot" style="background:var(--amber)"></span><div><strong>${escapeHtml(f.resourceId)}</strong><small>${escapeHtml(f.action || 'Review recommendation')} · ${formatMoney(f.savingsEstimate?.estimatedMonthlySavings)}</small></div></div>`).join('')}`;
}
function renderFindings(findings) {
const body = $('#findings-body');
if (!findings.length) { body.innerHTML = '<tr><td colspan="6"><div class="empty-state"><span>✓</span><strong>No idle resources found</strong><p>Your environment is looking efficient.</p></div></td></tr>'; return; }
body.innerHTML = findings.map(f => `<tr><td><span class="resource-id">${escapeHtml(f.resourceId)}</span><br><small>${Object.entries(f.tags || {}).map(([k,v]) => `${escapeHtml(k)}: ${escapeHtml(v)}`).join(' · ')}</small></td><td>${escapeHtml(f.region)}</td><td>${escapeHtml(f.reason)}</td><td><span class="status-chip warn">${escapeHtml(f.action || 'REVIEW')}</span></td><td><strong>${formatMoney(f.savingsEstimate?.estimatedMonthlySavings)}</strong><br><small>${escapeHtml(f.savingsEstimate?.currency || 'USD')}/month</small></td><td><span class="status-chip ${f.actionTaken ? 'done' : 'ready'}">${f.actionTaken ? 'Applied' : 'Ready'}</span></td></tr>`).join('');
}
async function runScan(button) {
setLoading(button, true); showToast('Scanning cloud telemetry…');
try { const response = await api('/api/v1/scan?dryRun=true', { method: 'POST' }); const result = await response.json(); renderScan(result); showToast('Scan complete — recommendations are ready.'); }
catch (error) { showToast(`Could not run scan: ${error.message}`, true); }
finally { setLoading(button, false); }
}
async function loadAudits() {
const body = $('#audits-body');
try { const response = await api('/api/v1/audits'); const audits = await response.json();
    if (!audits.length) return;
    body.innerHTML = audits.slice().reverse().map(a => `<tr><td><strong>${formatDate(a.scannedAt)}</strong></td><td><span class="status-chip ${a.dryRun ? 'ready' : 'warn'}">${a.dryRun ? 'Dry run' : 'Live'}</span></td><td>${a.totalResources}</td><td>${a.idleResources}</td><td><strong>${formatMoney(a.totalEstimatedMonthlySavings)}</strong></td><td>${escapeHtml(a.slackSummary || 'No Slack notification')}</td></tr>`).join('');
} catch (error) { showToast('Audit history is unavailable until the backend is running.', true); }
}
function createScanRecord(form) {
const data = new FormData(form); return { imageOrYamlName: data.get('imageOrYamlName'), scannedAt: new Date().toISOString(), criticalCount: Number(data.get('criticalCount')), highCount: Number(data.get('highCount')), mediumCount: Number(data.get('mediumCount')), lowCount: Number(data.get('lowCount')), findings: [] };
}
function createArtifact(form) { const data = new FormData(form); return { type: data.get('artifactType'), path: data.get('path'), content: data.get('content'), metadata: {} }; }
async function runDryRun(event) {
event.preventDefault(); const form = event.currentTarget; const button = $('#dry-run-button'); setLoading(button, true);
try { const request = { scanRecord: createScanRecord(form), artifact: createArtifact(form), policies: [{ id: 'no-root', ruleType: 'DISALLOW_ROOT_USER', blocking: true, value: '' }] }; const response = await api('/api/remediations/dry-run', { method: 'POST', body: JSON.stringify(request) }); const plan = await response.json(); state.latestRemediation = { request, plan }; renderRemediation(plan); showToast('Dry-run generated — review the proposed change.'); }
 catch (error) { showToast(`Dry-run failed: ${error.message}`, true); }
finally { setLoading(button, false); }
}
function renderRemediation(plan) {
const guardrails = plan.guardrailEvaluation || {}; const result = $('#remediation-result'); const vulnerabilities = plan.prioritizedScan?.vulnerabilities || []; const violations = guardrails.violations || [];
result.className = 'result-success'; result.innerHTML = `<div class="guardrail-result ${guardrails.allowed ? 'allowed' : 'blocked'}"><strong>${guardrails.allowed ? '✓ Guardrails passed' : '× Guardrails need attention'}</strong><span>${guardrails.allowed ? 'This proposal is eligible for review.' : `${violations.length} policy violation${violations.length === 1 ? '' : 's'} found.`}</span></div>${violations.map(v => `<div class="finding-mini"><strong>${escapeHtml(v.policyId)}</strong><span>${escapeHtml(v.message)}</span></div>`).join('')}${vulnerabilities.map(v => `<div class="finding-mini"><strong>${escapeHtml(v.finding?.cveId || 'Finding')} · ${escapeHtml(v.priority || 'REVIEW')}</strong><span>${escapeHtml(v.finding?.packageName || '')} · priority ${Number(v.priorityScore || 0).toFixed(1)}</span></div>`).join('') || '<div class="finding-mini"><strong>Artifact analyzed</strong><span>No vulnerability details were supplied in this dry-run.</span></div>'}`;
$('.score-ring').textContent = guardrails.allowed ? 'A' : 'C';
const diff = plan.fixProposal?.unifiedDiff || 'No automated source change was generated for this artifact.'; $('#diff-content').innerHTML = escapeHtml(diff).replace(/^\+(.*)$/gm, '<span class="diff-added">+$1</span>').replace(/^-(.*)$/gm, '<span class="diff-removed">-$1</span>'); $('#diff-panel').classList.remove('hidden');
}
async function downloadBinary(path, body, filename) { try { const response = await api(path, { method: 'POST', body: JSON.stringify(body) }); const blob = await response.blob(); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url); } catch (error) { showToast(`Download failed: ${error.message}`, true); } }

function bindEvents() {
$$('.nav-item,[data-view]').forEach(item => item.addEventListener('click', () => goTo(item.dataset.view)));
$('#menu-button').addEventListener('click', () => $('#sidebar').classList.toggle('open'));
$('#overview-scan').addEventListener('click', e => { goTo('cost'); runScan(e.currentTarget); }); $('#cost-scan').addEventListener('click', e => runScan(e.currentTarget));
$('#refresh-audits').addEventListener('click', () => loadAudits()); $('#remediation-form').addEventListener('submit', runDryRun);
$('#finding-search').addEventListener('input', e => { const query = e.target.value.toLowerCase(); $$('#findings-body tr').forEach(row => row.style.display = row.textContent.toLowerCase().includes(query) ? '' : 'none'); });
$('#download-patch').addEventListener('click', () => state.latestRemediation && downloadBinary('/api/remediations/patch', state.latestRemediation.request, 'vigidock-fix.patch'));
$('#download-pdf').addEventListener('click', () => { if (!state.latestRemediation?.request?.scanRecord) return showToast('Generate a remediation dry-run first.', true); downloadBinary('/api/reports/pdf', state.latestRemediation.request.scanRecord, 'vigidock-report.pdf'); });
$('#open-pr').addEventListener('click', async () => { if (!state.latestRemediation) return; const owner = window.prompt('GitHub owner'); const name = window.prompt('Repository name'); if (!owner || !name) return; try { const response = await api('/api/remediations/pull-request', { method:'POST', body: JSON.stringify({ remediation: state.latestRemediation.request, repository: { owner, name, baseBranch:'main', headBranch:'' } }) }); const result = await response.json(); showToast(`Pull request #${result.number} created.`); window.open(result.url, '_blank'); } catch (error) { showToast(`Pull request was not created: ${error.message}`, true); } });
$('#save-settings').addEventListener('click', () => { const value = $('#api-url').value.trim().replace(/\/$/, ''); if (!value) return showToast('Enter a valid backend URL.', true); state.apiUrl = value; localStorage.setItem('greenops-api-url', value); $('#connection-status').textContent = 'Saved'; $('#connection-status').className = 'pill green'; showToast('Workspace settings saved.'); });
}
bindEvents(); $('#api-url').value = state.apiUrl;
