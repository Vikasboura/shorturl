import React, { useEffect, useState } from 'react';
import { X, ExternalLink, Globe, Laptop, Chrome, Activity } from 'lucide-react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js';
import { Bar } from 'react-chartjs-2';

ChartJS.register(
  CategoryScale,
  LinearScale,
  BarElement,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

export default function AnalyticsModal({ shortCode, onClose, apiBaseUrl }) {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    async function fetchStats() {
      try {
        setLoading(true);
        const res = await fetch(`${apiBaseUrl}/stats/${shortCode}`);
        if (!res.ok) {
          throw new Error(`Failed to load stats: ${res.statusText}`);
        }
        const data = await res.json();
        setStats(data);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }
    if (shortCode) {
      fetchStats();
    }
  }, [shortCode, apiBaseUrl]);

  if (!shortCode) return null;

  const chartData = {
    labels: stats?.clicksPerDay ? Object.keys(stats.clicksPerDay) : [],
    datasets: [
      {
        label: 'Clicks per Day',
        data: stats?.clicksPerDay ? Object.values(stats.clicksPerDay) : [],
        backgroundColor: '#ec7211',
        borderRadius: 4,
      }
    ]
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      title: { display: false }
    },
    scales: {
      y: {
        beginAtZero: true,
        ticks: { stepSize: 1 }
      }
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Activity size={20} color="#ec7211" />
            <h2 style={{ fontSize: 18, fontWeight: 700 }}>
              Analytics for <span className="font-mono">/{shortCode}</span>
            </h2>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
            <X size={20} color="#545b64" />
          </button>
        </div>

        <div className="modal-body">
          {loading && <p style={{ color: '#545b64' }}>Loading analytics telemetry...</p>}
          {error && <div className="banner-error">{error}</div>}

          {stats && (
            <>
              {/* Summary Cards */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginBottom: 20 }}>
                <div style={{ background: '#f8f9fa', padding: 14, borderRadius: 6, border: '1px solid #eaeded' }}>
                  <div style={{ fontSize: 12, color: '#545b64', fontWeight: 600 }}>TOTAL CLICKS</div>
                  <div style={{ fontSize: 24, fontWeight: 700, color: '#ec7211', marginTop: 4 }}>
                    {stats.totalClicks}
                  </div>
                </div>
                <div style={{ background: '#f8f9fa', padding: 14, borderRadius: 6, border: '1px solid #eaeded' }}>
                  <div style={{ fontSize: 12, color: '#545b64', fontWeight: 600 }}>DESTINATION</div>
                  <div style={{ fontSize: 13, marginTop: 4, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    <a href={stats.longUrl} target="_blank" rel="noreferrer" style={{ color: '#0073bb' }}>
                      {stats.longUrl}
                    </a>
                  </div>
                </div>
                <div style={{ background: '#f8f9fa', padding: 14, borderRadius: 6, border: '1px solid #eaeded' }}>
                  <div style={{ fontSize: 12, color: '#545b64', fontWeight: 600 }}>TTL STATUS</div>
                  <div style={{ fontSize: 13, fontWeight: 600, marginTop: 4 }}>
                    {stats.expiresAt ? new Date(stats.expiresAt * 1000).toLocaleString() : 'Permanent (No Expiry)'}
                  </div>
                </div>
              </div>

              {/* Chart */}
              <div style={{ marginBottom: 24 }}>
                <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 8, color: '#16191f' }}>
                  Daily Click Activity
                </h3>
                <div className="chart-container">
                  {Object.keys(stats.clicksPerDay || {}).length === 0 ? (
                    <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#879596' }}>
                      No click events logged yet. Test by opening the short link!
                    </div>
                  ) : (
                    <Bar data={chartData} options={chartOptions} />
                  )}
                </div>
              </div>

              {/* Breakdowns */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
                {/* Referrers */}
                <div>
                  <h4 style={{ fontSize: 13, fontWeight: 600, color: '#545b64', marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6 }}>
                    <Globe size={16} /> Top Referrers
                  </h4>
                  <div style={{ background: '#fafafa', border: '1px solid #eaeded', borderRadius: 6, padding: 12 }}>
                    {Object.entries(stats.topReferrers || {}).length === 0 ? (
                      <p style={{ fontSize: 12, color: '#879596' }}>No referrer data yet</p>
                    ) : (
                      Object.entries(stats.topReferrers).map(([ref, count]) => (
                        <div key={ref} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid #f0f0f0', fontSize: 13 }}>
                          <span style={{ color: '#16191f' }}>{ref}</span>
                          <span style={{ fontWeight: 600, color: '#ec7211' }}>{count}</span>
                        </div>
                      ))
                    )}
                  </div>
                </div>

                {/* Browsers & OS */}
                <div>
                  <h4 style={{ fontSize: 13, fontWeight: 600, color: '#545b64', marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6 }}>
                    <Chrome size={16} /> Browsers & OS
                  </h4>
                  <div style={{ background: '#fafafa', border: '1px solid #eaeded', borderRadius: 6, padding: 12 }}>
                    <div style={{ marginBottom: 10 }}>
                      <div style={{ fontSize: 11, fontWeight: 600, color: '#879596', textTransform: 'uppercase' }}>Browsers</div>
                      {Object.entries(stats.browserDistribution || {}).map(([b, count]) => (
                        <div key={b} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: 12 }}>
                          <span>{b}</span>
                          <span style={{ fontWeight: 600 }}>{count}</span>
                        </div>
                      ))}
                    </div>
                    <div>
                      <div style={{ fontSize: 11, fontWeight: 600, color: '#879596', textTransform: 'uppercase' }}>Operating Systems</div>
                      {Object.entries(stats.osDistribution || {}).map(([os, count]) => (
                        <div key={os} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: 12 }}>
                          <span>{os}</span>
                          <span style={{ fontWeight: 600 }}>{count}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
