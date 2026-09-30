import React, { useState, useEffect } from 'react';
import { 
  Link2, 
  ExternalLink, 
  Copy, 
  Check, 
  BarChart2, 
  Cpu, 
  ShieldCheck, 
  Clock, 
  Search, 
  RefreshCw,
  PlusCircle,
  Database
} from 'lucide-react';
import AnalyticsModal from './components/AnalyticsModal';
import CacheMetricsModal from './components/CacheMetricsModal';

const API_BASE = window.location.origin.includes(':5173') 
  ? 'http://localhost:8080' 
  : '';

export default function App() {
  const [links, setLinks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [successLink, setSuccessLink] = useState(null);
  const [copiedCode, setCopiedCode] = useState(null);

  // Form State
  const [longUrl, setLongUrl] = useState('');
  const [customAlias, setCustomAlias] = useState('');
  const [ttlOption, setTtlOption] = useState('0');
  const [submitting, setSubmitting] = useState(false);

  // Modals
  const [selectedCodeForStats, setSelectedCodeForStats] = useState(null);
  const [showCacheModal, setShowCacheModal] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Load recent links
  async function loadLinks() {
    try {
      setLoading(true);
      const res = await fetch(`${API_BASE}/api/links/recent?limit=50`);
      if (res.ok) {
        const data = await res.json();
        setLinks(data);
      }
    } catch (e) {
      console.error("Failed to load links:", e);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadLinks();
  }, []);

  async function handleCreateShortLink(e) {
    e.preventDefault();
    if (!longUrl) return;

    setError(null);
    setSuccessLink(null);
    setSubmitting(true);

    try {
      const payload = {
        url: longUrl,
        customAlias: customAlias ? customAlias.trim() : null,
        ttlSeconds: ttlOption !== '0' ? parseInt(ttlOption) : null,
        creatorId: 'console-user'
      };

      const res = await fetch(`${API_BASE}/shorten`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Failed to create short link');
      }

      setSuccessLink(data);
      setLongUrl('');
      setCustomAlias('');
      setTtlOption('0');
      loadLinks(); // Refresh table
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  function copyToClipboard(text, code) {
    navigator.clipboard.writeText(text);
    setCopiedCode(code);
    setTimeout(() => setCopiedCode(null), 2000);
  }

  const filteredLinks = links.filter(l => 
    l.shortCode?.toLowerCase().includes(searchQuery.toLowerCase()) ||
    l.longUrl?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div>
      {/* Amazon Console Top Navigation */}
      <nav className="top-nav">
        <div className="nav-brand">
          <Link2 size={22} color="#ec7211" />
          <span>ShortLink Console</span>
          <span style={{ color: '#879596' }}>|</span>
          <span style={{ fontWeight: 500 }}>URL Routing & Analytics</span>
          <span className="brand-badge">PROD</span>
        </div>
        <div className="nav-actions">
          <div className="region-badge">
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#1d8102', display: 'inline-block' }}></span>
            us-east-1 (DynamoDB Multi-AZ)
          </div>
          <button className="btn-secondary-nav" onClick={() => setShowCacheModal(true)}>
            <Cpu size={14} /> LRU Cache Telemetry
          </button>
        </div>
      </nav>

      {/* Main Content Area */}
      <main className="main-container">
        
        {/* KPI Metrics Ribbon */}
        <section className="stats-grid">
          <div className="stat-card">
            <div className="stat-header">
              <span>TOTAL SHORT LINKS</span>
              <Database size={16} color="#0073bb" />
            </div>
            <div className="stat-value">{links.length}</div>
            <div className="stat-sub">Persistent in AWS DynamoDB</div>
          </div>

          <div className="stat-card">
            <div className="stat-header">
              <span>READ PATH TARGET</span>
              <Clock size={16} color="#1d8102" />
            </div>
            <div className="stat-value">&lt; 15 ms</div>
            <div className="stat-sub">p99 redirect SLA</div>
          </div>

          <div className="stat-card">
            <div className="stat-header">
              <span>RATE LIMIT POLICY</span>
              <ShieldCheck size={16} color="#ec7211" />
            </div>
            <div className="stat-value">60 req / min</div>
            <div className="stat-sub">Per-IP Token Bucket</div>
          </div>

          <div className="stat-card">
            <div className="stat-header">
              <span>HOT LINK CACHING</span>
              <Cpu size={16} color="#6b21a8" />
            </div>
            <div className="stat-value">In-Memory LRU</div>
            <div className="stat-sub">Bypasses DB on hit</div>
          </div>
        </section>

        {/* Shorten Link Form Card */}
        <section className="card">
          <div className="card-header">
            <h2 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <PlusCircle size={18} color="#ec7211" /> Create Short Link
            </h2>
            <span style={{ fontSize: 12, color: '#545b64' }}>
              Base62 random generation or vanity custom alias with DynamoDB conditional put
            </span>
          </div>
          <div className="card-body">
            <form onSubmit={handleCreateShortLink}>
              <div className="form-grid">
                <div className="form-group">
                  <label className="form-label">Destination Long URL *</label>
                  <input
                    type="url"
                    className="form-input"
                    placeholder="https://aws.amazon.com/dynamodb/features"
                    value={longUrl}
                    onChange={(e) => setLongUrl(e.target.value)}
                    required
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Custom Alias (Optional)</label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="e.g. prime-deals-2026"
                    value={customAlias}
                    onChange={(e) => setCustomAlias(e.target.value)}
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">TTL Expiry (DynamoDB TTL)</label>
                  <select
                    className="form-select"
                    value={ttlOption}
                    onChange={(e) => setTtlOption(e.target.value)}
                  >
                    <option value="0">Never (Permanent)</option>
                    <option value="3600">1 Hour</option>
                    <option value="86400">24 Hours</option>
                    <option value="604800">7 Days</option>
                    <option value="2592000">30 Days</option>
                  </select>
                </div>

                <button type="submit" className="btn-primary" disabled={submitting}>
                  {submitting ? 'Generating...' : 'Shorten Link'}
                </button>
              </div>
            </form>

            {/* Error Notification */}
            {error && (
              <div className="banner-error">
                <strong>Error: </strong> {error}
              </div>
            )}

            {/* Success Notification */}
            {successLink && (
              <div className="banner-success">
                <div>
                  <div style={{ fontWeight: 600, color: '#137333', fontSize: 14 }}>
                    Short link created successfully!
                  </div>
                  <div style={{ marginTop: 4, display: 'flex', alignItems: 'center', gap: 10 }}>
                    <a
                      href={successLink.shortUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="code-pill"
                    >
                      {successLink.shortUrl}
                    </a>
                    <span style={{ fontSize: 12, color: '#545b64' }}>
                      Redirects to: <span style={{ color: '#16191f' }}>{successLink.longUrl}</span>
                    </span>
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button
                    className="btn-secondary"
                    onClick={() => copyToClipboard(successLink.shortUrl, 'SUCCESS')}
                  >
                    {copiedCode === 'SUCCESS' ? <Check size={14} color="#1d8102" /> : <Copy size={14} />}
                    {copiedCode === 'SUCCESS' ? 'Copied' : 'Copy URL'}
                  </button>
                  <a
                    href={successLink.shortUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="btn-primary"
                    style={{ textDecoration: 'none', height: 32, fontSize: 13, padding: '0 12px' }}
                  >
                    <ExternalLink size={14} /> Test 302
                  </a>
                </div>
              </div>
            )}
          </div>
        </section>

        {/* Links Data Table Card */}
        <section className="card">
          <div className="card-header">
            <h2 className="card-title">Active Short Links ({filteredLinks.length})</h2>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{ position: 'relative' }}>
                <Search size={14} style={{ position: 'absolute', left: 10, top: 12, color: '#879596' }} />
                <input
                  type="text"
                  placeholder="Filter by code or URL..."
                  className="form-input"
                  style={{ paddingLeft: 30, width: 220, height: 36, fontSize: 13 }}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>
              <button className="btn-secondary" onClick={loadLinks}>
                <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
              </button>
            </div>
          </div>

          <div style={{ overflowX: 'auto' }}>
            <table className="links-table">
              <thead>
                <tr>
                  <th>Short Code</th>
                  <th>Destination URL</th>
                  <th>Type</th>
                  <th>Created</th>
                  <th>TTL / Expiration</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredLinks.length === 0 ? (
                  <tr>
                    <td colSpan="6" style={{ textAlign: 'center', padding: 36, color: '#879596' }}>
                      {loading ? 'Fetching records from DynamoDB...' : 'No short links found. Create your first link above!'}
                    </td>
                  </tr>
                ) : (
                  filteredLinks.map((link) => {
                    const isExpired = link.expiresAt && Date.now() / 1000 > link.expiresAt;
                    const fullUrl = `${API_BASE}/${link.shortCode}`;
                    return (
                      <tr key={link.shortCode}>
                        <td>
                          <a
                            href={fullUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="code-pill"
                          >
                            /{link.shortCode} <ExternalLink size={12} />
                          </a>
                        </td>
                        <td style={{ maxWidth: 360, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          <span title={link.longUrl}>{link.longUrl}</span>
                        </td>
                        <td>
                          {link.customAlias ? (
                            <span className="badge badge-alias">Custom Alias</span>
                          ) : (
                            <span className="badge" style={{ background: '#f0f0f0', color: '#545b64' }}>Base62 Random</span>
                          )}
                        </td>
                        <td style={{ color: '#545b64' }}>
                          {link.createdAt ? new Date(link.createdAt).toLocaleDateString() : 'N/A'}
                        </td>
                        <td>
                          {isExpired ? (
                            <span className="badge badge-expired">Expired</span>
                          ) : link.expiresAt ? (
                            <span className="badge badge-active" title={new Date(link.expiresAt * 1000).toLocaleString()}>
                              TTL Active
                            </span>
                          ) : (
                            <span style={{ color: '#879596', fontSize: 12 }}>Permanent</span>
                          )}
                        </td>
                        <td>
                          <div style={{ display: 'flex', gap: 6 }}>
                            <button
                              className="btn-secondary"
                              onClick={() => setSelectedCodeForStats(link.shortCode)}
                              title="View real-time click telemetry"
                            >
                              <BarChart2 size={13} color="#ec7211" /> Stats
                            </button>
                            <button
                              className="btn-secondary"
                              onClick={() => copyToClipboard(fullUrl, link.shortCode)}
                              title="Copy URL to clipboard"
                            >
                              {copiedCode === link.shortCode ? (
                                <Check size={13} color="#1d8102" />
                              ) : (
                                <Copy size={13} />
                              )}
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </section>
      </main>

      {/* Analytics Modal */}
      {selectedCodeForStats && (
        <AnalyticsModal
          shortCode={selectedCodeForStats}
          onClose={() => setSelectedCodeForStats(null)}
          apiBaseUrl={API_BASE}
        />
      )}

      {/* Cache Metrics Modal */}
      {showCacheModal && (
        <CacheMetricsModal
          onClose={() => setShowCacheModal(false)}
          apiBaseUrl={API_BASE}
        />
      )}
    </div>
  );
}
