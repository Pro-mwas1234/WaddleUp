(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.WaddleSite = factory();
    if (typeof document !== 'undefined') {
      document.addEventListener('DOMContentLoaded', () => root.WaddleSite.init());
    }
  }
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const DEFAULT_REPO = 'Pro-mwas1234/WaddleUp';

  function detectRepo(locationObj) {
    if (!locationObj) return DEFAULT_REPO;
    try {
      const search = new URLSearchParams(locationObj.search || '');
      const override = search.get('repo');
      if (override && /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(override.trim())) {
        return override.trim();
      }
      const host = (locationObj.hostname || '').toLowerCase();
      if (host.endsWith('.github.io')) {
        const owner = host.slice(0, -'.github.io'.length);
        const pathPart = (locationObj.pathname || '/')
          .split('/')
          .filter(Boolean)[0];
        if (owner && pathPart) {
          return `${owner}/${pathPart}`;
        }
      }
    } catch (_) {
      // Fallback to default repository slug
    }
    return DEFAULT_REPO;
  }

  function formatBytes(bytes) {
    if (!Number.isFinite(bytes) || bytes <= 0) return 'Direct APK';
    const units = ['B', 'KB', 'MB', 'GB'];
    let value = bytes;
    let index = 0;
    while (value >= 1024 && index < units.length - 1) {
      value /= 1024;
      index += 1;
    }
    return `${value.toFixed(index >= 2 ? 1 : 0)} ${units[index]}`;
  }

  function formatReleaseDate(isoDate, nowMs = Date.now()) {
    if (!isoDate) return 'Auto-updated';
    const parsed = Date.parse(isoDate);
    if (Number.isNaN(parsed)) return 'Auto-updated';

    const diffMs = Math.max(0, nowMs - parsed);
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    const diffDays = Math.floor(diffHours / 24);

    if (diffHours < 1) return 'Just now';
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 7) return `${diffDays} days ago`;

    return new Date(parsed).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  }

  function pickApkAsset(assets) {
    if (!Array.isArray(assets)) return null;
    const apks = assets.filter(
      (asset) =>
        asset &&
        typeof asset.name === 'string' &&
        asset.name.toLowerCase().endsWith('.apk') &&
        typeof asset.browser_download_url === 'string'
    );
    if (apks.length === 0) return null;

    // Prefer the versioned APK filename (e.g. waddleup-v1.0.3.apk) over waddleup-latest.apk
    return (
      apks.find((asset) => asset.name.toLowerCase() !== 'waddleup-latest.apk') ||
      apks[0]
    );
  }

  function normalizeRelease(raw, fallbackRepo) {
    if (!raw || raw.draft) return null;
    const apk = pickApkAsset(raw.assets);
    const totalDownloads = Array.isArray(raw.assets)
      ? raw.assets
          .filter((a) => a && typeof a.name === 'string' && a.name.toLowerCase().endsWith('.apk'))
          .reduce((sum, a) => sum + (Number(a.download_count) || 0), 0)
      : 0;

    const tag = raw.tag_name || raw.name || 'latest';
    const digestRaw = (apk && apk.digest) || raw.apkSha256 || '';
    const sha256 = digestRaw.replace(/^sha256:/i, '').trim() || null;

    return {
      tag,
      name: raw.name || `WaddleUp ${tag}`,
      prerelease: Boolean(raw.prerelease),
      publishedAt: raw.published_at || raw.created_at || null,
      body: typeof raw.body === 'string' ? raw.body : '',
      htmlUrl: raw.html_url || `https://github.com/${fallbackRepo}/releases/tag/${encodeURIComponent(tag)}`,
      apkName: apk ? apk.name : 'waddleup-latest.apk',
      apkUrl: apk
        ? apk.browser_download_url
        : `https://github.com/${fallbackRepo}/releases/latest/download/waddleup-latest.apk`,
      apkSize: apk ? Number(apk.size) || 0 : 0,
      apkSha256: sha256,
      downloads: totalDownloads,
    };
  }

  function mergeReleases(liveList, snapshotList, repo) {
    const snapshotByTag = new Map();
    for (const item of snapshotList || []) {
      const normalized = normalizeRelease(item, repo);
      if (normalized) {
        snapshotByTag.set(normalized.tag, normalized);
      }
    }

    const liveNormalized = (liveList || [])
      .map((item) => normalizeRelease(item, repo))
      .filter(Boolean);

    if (liveNormalized.length > 0) {
      return liveNormalized.map((rel) => {
        const snap = snapshotByTag.get(rel.tag);
        if (snap && !rel.apkSha256 && snap.apkSha256) {
          return { ...rel, apkSha256: snap.apkSha256 };
        }
        return rel;
      });
    }

    return Array.from(snapshotByTag.values());
  }

  function pickLatestRelease(releases) {
    if (!Array.isArray(releases) || releases.length === 0) return null;
    return releases.find((r) => !r.prerelease) || releases[0];
  }

  function escapeHtml(text) {
    return String(text)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatInlineMarkdown(line) {
    let escaped = escapeHtml(line);
    escaped = escaped.replace(/`([^`]+)`/g, '<code>$1</code>');
    escaped = escaped.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    return escaped;
  }

  function renderReleaseNotesHtml(markdown) {
    const clean = (markdown || '').trim();
    if (!clean) {
      return '<p>Freshly built and signed Android release ready to install.</p>';
    }

    const lines = clean.split(/\r?\n/);
    const blocks = [];
    let inList = false;

    const closeList = () => {
      if (inList) {
        blocks.push('</ul>');
        inList = false;
      }
    };

    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line) {
        closeList();
        continue;
      }

      const headingMatch = line.match(/^(#{1,4})\s+(.+)$/);
      if (headingMatch) {
        closeList();
        const level = Math.min(4, Math.max(2, headingMatch[1].length));
        blocks.push(`<h${level}>${formatInlineMarkdown(headingMatch[2])}</h${level}>`);
        continue;
      }

      const listMatch = line.match(/^[-*]\s+(.+)$/);
      if (listMatch) {
        if (!inList) {
          blocks.push('<ul>');
          inList = true;
        }
        blocks.push(`<li>${formatInlineMarkdown(listMatch[1])}</li>`);
        continue;
      }

      const quoteMatch = line.match(/^>\s*(.+)$/);
      if (quoteMatch) {
        closeList();
        blocks.push(`<blockquote>${formatInlineMarkdown(quoteMatch[1])}</blockquote>`);
        continue;
      }

      closeList();
      blocks.push(`<p>${formatInlineMarkdown(line)}</p>`);
    }

    closeList();
    return blocks.join('\n');
  }

  async function fetchJsonSafe(url, timeoutMs = 6000) {
    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    const timer = controller ? setTimeout(() => controller.abort(), timeoutMs) : null;
    try {
      const res = await fetch(url, {
        cache: 'no-store',
        headers: { Accept: 'application/vnd.github+json' },
        signal: controller ? controller.signal : undefined,
      });
      if (!res.ok) return null;
      return await res.json();
    } catch (_) {
      return null;
    } finally {
      if (timer) clearTimeout(timer);
    }
  }

  function showToast(message) {
    const toast = document.getElementById('toast');
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add('visible');
    clearTimeout(showToast._timer);
    showToast._timer = setTimeout(() => toast.classList.remove('visible'), 2400);
  }

  async function copyText(text, successLabel) {
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(text);
        showToast(successLabel);
        return;
      }
    } catch (_) {
      // Fallback below
    }
    const input = document.createElement('input');
    input.value = text;
    document.body.appendChild(input);
    input.select();
    document.execCommand('copy');
    input.remove();
    showToast(successLabel);
  }

  function applyStaticRepoLinks(repo) {
    const base = `https://github.com/${repo}`;
    const latestApk = `${base}/releases/latest/download/waddleup-latest.apk`;

    const ids = {
      'header-download-btn': latestApk,
      'primary-download-btn': latestApk,
      'github-release-link': `${base}/releases/latest`,
      'all-releases-link': `${base}/releases`,
      'footer-repo-link': base,
      'footer-direct-link': latestApk,
      'footer-releases-link': `${base}/releases`,
    };

    Object.entries(ids).forEach(([id, href]) => {
      const el = document.getElementById(id);
      if (el) el.href = href;
    });
  }

  function renderLatest(latest, repo, sourceLabel) {
    const syncLabel = document.getElementById('sync-status-label');
    const versionPill = document.getElementById('latest-version-pill');
    const statVersion = document.getElementById('stat-version');
    const statSize = document.getElementById('stat-size');
    const statDate = document.getElementById('stat-date');
    const primaryBtn = document.getElementById('primary-download-btn');
    const primaryLabel = document.getElementById('primary-download-label');
    const headerBtn = document.getElementById('header-download-btn');
    const releaseLink = document.getElementById('github-release-link');
    const changelogTitle = document.getElementById('changelog-title');
    const changelogBody = document.getElementById('changelog-body');
    const shaBtn = document.getElementById('sha-copy-btn');
    const shaPreview = document.getElementById('sha-preview');

    if (syncLabel) syncLabel.textContent = sourceLabel;

    if (!latest) {
      if (changelogBody) {
        changelogBody.innerHTML =
          '<p>No published GitHub Releases found yet. Once your first release finishes building, this page updates automatically.</p>';
      }
      return `https://github.com/${repo}/releases/latest/download/waddleup-latest.apk`;
    }

    if (versionPill) versionPill.textContent = latest.tag;
    if (statVersion) statVersion.textContent = latest.tag;
    if (statSize) statSize.textContent = formatBytes(latest.apkSize);
    if (statDate) statDate.textContent = formatReleaseDate(latest.publishedAt);

    if (primaryBtn) {
      primaryBtn.href = latest.apkUrl;
      primaryBtn.setAttribute('download', latest.apkName);
    }
    if (primaryLabel) {
      primaryLabel.textContent = `Download ${latest.tag} APK`;
    }
    if (headerBtn) {
      headerBtn.href = latest.apkUrl;
      headerBtn.setAttribute('download', latest.apkName);
      headerBtn.textContent = `Get ${latest.tag}`;
    }
    if (releaseLink) {
      releaseLink.href = latest.htmlUrl;
    }
    if (changelogTitle) {
      changelogTitle.textContent = `${latest.name} Notes`;
    }
    if (changelogBody) {
      changelogBody.innerHTML = renderReleaseNotesHtml(latest.body);
    }

    if (shaBtn && shaPreview) {
      if (latest.apkSha256) {
        shaBtn.hidden = false;
        shaPreview.textContent = `${latest.apkSha256.slice(0, 12)}…`;
        shaBtn.onclick = () => copyText(latest.apkSha256, 'APK SHA-256 copied');
      } else {
        shaBtn.hidden = true;
      }
    }

    return latest.apkUrl;
  }

  function renderReleaseList(releases, latestTag) {
    const container = document.getElementById('releases-list');
    if (!container) return;

    if (!releases || releases.length === 0) {
      container.innerHTML =
        '<div class="empty-state">No published releases yet. Use the direct download button once your first GitHub Release publishes.</div>';
      return;
    }

    container.innerHTML = '';

    releases.forEach((rel, index) => {
      const card = document.createElement('article');
      card.className = 'release-item';

      const head = document.createElement('div');
      head.className = 'release-item-head';

      const meta = document.createElement('div');
      meta.className = 'release-item-meta';

      const title = document.createElement('span');
      title.className = 'release-item-title';
      title.textContent = rel.name;
      meta.appendChild(title);

      if (rel.tag === latestTag) {
        const badge = document.createElement('span');
        badge.className = 'pill pill-live';
        badge.textContent = 'Latest';
        meta.appendChild(badge);
      } else if (rel.prerelease) {
        const badge = document.createElement('span');
        badge.className = 'pill pill-android';
        badge.textContent = 'Pre-release';
        meta.appendChild(badge);
      }

      const sub = document.createElement('span');
      sub.className = 'release-item-sub';
      const parts = [formatReleaseDate(rel.publishedAt), formatBytes(rel.apkSize)];
      if (rel.downloads > 0) parts.push(`${rel.downloads} downloads`);
      sub.textContent = parts.join(' • ');
      meta.appendChild(sub);

      const actions = document.createElement('div');
      actions.className = 'release-item-actions';

      const dlBtn = document.createElement('a');
      dlBtn.className = 'btn btn-primary btn-sm';
      dlBtn.href = rel.apkUrl;
      dlBtn.setAttribute('download', rel.apkName);
      dlBtn.textContent = `Download ${rel.tag}`;
      actions.appendChild(dlBtn);

      const notesBtn = document.createElement('button');
      notesBtn.className = 'btn btn-secondary btn-sm';
      notesBtn.type = 'button';
      notesBtn.textContent = index === 0 ? 'Hide Notes' : 'Notes';
      actions.appendChild(notesBtn);

      const notesBox = document.createElement('div');
      notesBox.className = 'release-item-notes changelog-body';
      notesBox.hidden = index !== 0;
      notesBox.innerHTML = renderReleaseNotesHtml(rel.body);

      notesBtn.addEventListener('click', () => {
        const isHidden = notesBox.hidden;
        notesBox.hidden = !isHidden;
        notesBtn.textContent = isHidden ? 'Hide Notes' : 'Notes';
      });

      head.appendChild(meta);
      head.appendChild(actions);
      card.appendChild(head);
      card.appendChild(notesBox);
      container.appendChild(card);
    });
  }

  async function init() {
    const repo = detectRepo(window.location);
    applyStaticRepoLinks(repo);

    const footerMeta = document.getElementById('footer-meta');
    if (footerMeta) {
      footerMeta.textContent = `WaddleUp Android • ${repo} • Auto-updated with GitHub Releases`;
    }

    let currentDirectUrl = `https://github.com/${repo}/releases/latest/download/waddleup-latest.apk`;

    const copyBtn = document.getElementById('copy-link-btn');
    if (copyBtn) {
      copyBtn.addEventListener('click', () => copyText(currentDirectUrl, 'Direct APK link copied'));
    }

    const shareBtn = document.getElementById('share-btn');
    if (shareBtn) {
      shareBtn.addEventListener('click', async () => {
        const shareData = {
          title: 'WaddleUp Android APK',
          text: 'Download the latest WaddleUp Android build directly:',
          url: window.location.href,
        };
        if (navigator.share) {
          try {
            await navigator.share(shareData);
            return;
          } catch (_) {
            // Fall through to copy page link
          }
        }
        copyText(window.location.href, 'Download page URL copied');
      });
    }

    const [snapshotData, liveData] = await Promise.all([
      fetchJsonSafe('releases.json'),
      fetchJsonSafe(`https://api.github.com/repos/${repo}/releases?per_page=10`),
    ]);

    const snapshotReleases = snapshotData && Array.isArray(snapshotData.releases) ? snapshotData.releases : [];
    const liveReleases = Array.isArray(liveData) ? liveData : [];

    const releases = mergeReleases(liveReleases, snapshotReleases, repo);
    const latest = pickLatestRelease(releases);
    const sourceLabel = liveReleases.length > 0
      ? 'Live from GitHub Releases'
      : snapshotReleases.length > 0
        ? 'Synced from Release Snapshot'
        : 'Direct Latest APK Ready';

    currentDirectUrl = renderLatest(latest, repo, sourceLabel);
    renderReleaseList(releases, latest ? latest.tag : null);
  }

  return {
    detectRepo,
    formatBytes,
    formatReleaseDate,
    pickApkAsset,
    normalizeRelease,
    mergeReleases,
    pickLatestRelease,
    renderReleaseNotesHtml,
    init,
  };
});
