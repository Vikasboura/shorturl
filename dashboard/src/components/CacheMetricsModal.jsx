import React, { useEffect, useState } from 'react';
import { X, Cpu, CheckCircle2, AlertCircle, RefreshCw } from 'lucide-react';

export default function CacheMetricsModal({ onClose, apiBaseUrl }) {
  const [cacheStats, setCacheStats] = useState(null);
  const [loading, setLoading] = useState(true);

  async function fetchCacheMetrics() {
    try {
      setLoading(true);
      const res = await fetch(`${apiBaseUrl}/api/cache/metrics`);
      if (res.ok) {
        const data = await res.json();
        setCacheStats(data);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchCacheMetrics();
  }, [apiBaseUrl]);

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 550 }}>
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Cpu size={20} color="#ec7211" />
            <h2 style={{ fontSize: 18, fontWeight: 700 }}>In-Memory LRU Cache Telemetry</h2>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
            <X size={20} color="#545b64" />
          </button>
        </div>

        <div className="modal-body">
          <p style={{ fontSize: 13, color: '#545b64', marginBottom: 20 }}>
            Custom HashMap + Doubly Linked List tier protecting DynamoDB from hot key partition throttling.
          </p>

          {loading && <p>Loading cache metrics...</p>}

          {cacheStats && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
              <div style={{ background: '#f8f9fa', padding: 14, borderRadius: 6, border: '1px solid #eaeded' }}>
                <div style={{ fontSize: 12, color: '#545b64', fontWeight: 600 }}>HIT RATIO</div>
                <div style={{ fontSize: 28, fontWeight: 700, color: '#1d8102', marginTop: 4 }}>
                  {(cacheStats.hitRatio * 100).toFixed(1)}%
                </div>
              </div>

              <div style={{ background: '#f8f9fa', padding: 14, borderRadius: 6, border: '1px solid #eaeded' }}>
                <div style={{ fontSize: 12, color: '#545b64', fontWeight: 600 }}>CURRENT CAPACITY</div>
                <div style={{ fontSize: 24, fontWeight: 700, color: '#16191f', marginTop: 6 }}>
                  {cacheStats.currentSize} / {cacheStats.capacity}
                </div>
              </div>

              <div style={{ background: '#f8f9fa', padding: 14, borderRadius: 6, border: '1px solid #eaeded' }}>
                <div style={{ fontSize: 12, color: '#545b64', fontWeight: 600 }}>CACHE HITS</div>
                <div style={{ fontSize: 22, fontWeight: 600, color: '#0073bb', marginTop: 4 }}>
                  {cacheStats.hitCount}
                </div>
              </div>

              <div style={{ background: '#f8f9fa', padding: 14, borderRadius: 6, border: '1px solid #eaeded' }}>
                <div style={{ fontSize: 12, color: '#545b64', fontWeight: 600 }}>CACHE MISSES</div>
                <div style={{ fontSize: 22, fontWeight: 600, color: '#d13212', marginTop: 4 }}>
                  {cacheStats.missCount}
                </div>
              </div>

              <div style={{ gridColumn: 'span 2', background: '#f8f9fa', padding: 14, borderRadius: 6, border: '1px solid #eaeded' }}>
                <div style={{ fontSize: 12, color: '#545b64', fontWeight: 600 }}>EVICTIONS (LRU TAIL DROPS)</div>
                <div style={{ fontSize: 20, fontWeight: 600, color: '#545b64', marginTop: 4 }}>
                  {cacheStats.evictionCount}
                </div>
              </div>
            </div>
          )}

          <div style={{ marginTop: 24, display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
            <button className="btn-secondary" onClick={fetchCacheMetrics}>
              <RefreshCw size={14} /> Refresh
            </button>
            <button className="btn-primary" onClick={onClose}>
              Done
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
