# Laze Iraq Navigation Infrastructure (بنية ملاحة لايز الخاصة بالعراق)

This directory contains the production-grade, self-hosted navigation infrastructure tailored for Iraq. It frees the Laze app from dependency on public community servers (FOSSGIS, Project OSRM) which enforce aggressive fair-use rate limits and frequently fail on long routes (such as Baghdad ↔ Erbil or Baghdad ↔ Basra).

---

## Services Included

1. **OSRM Iraq Routing (`osrm-iraq`)**:
   - High-performance routing using the **Multi-Level Dijkstra (MLD)** algorithm.
   - Grounded strictly in Geofabrik's Iraq OpenStreetMap extract (`iraq-latest.osm.pbf`).
   - Supports long-distance routes across all Iraqi governorates in sub-50ms latency.
2. **Photon Search (`photon`)**:
   - Geocoder optimized for Arabic queries (بغداد، أربيل، المنصور، الكرادة، إلخ).
3. **Vector Tile Server (`tileserver`)**:
   - Serves OpenMapTiles PMTiles for Iraq with MapLibre GL styling.
4. **Nginx API Gateway (`nginx`)**:
   - Rate limiting (30 requests/sec with burst of 50 per IP).
   - Unified reverse proxy with SSL termination and gzip compression.

---

## Deployment Instructions

### Prerequisites
- VPS running Ubuntu 22.04 / 24.04 LTS (Minimum: 4 CPU cores, 8GB RAM, 50GB NVMe SSD).
- Docker and Docker Compose v2.

### Quick Start
```bash
cd infra
chmod +x scripts/setup-iraq.sh
./scripts/setup-iraq.sh
```

### Verification
```bash
# Health check
curl http://localhost/health

# Test long route (Baghdad to Erbil)
curl "http://localhost/route/v1/driving/44.3615,33.3152;44.0092,36.1901?overview=full&steps=true"
```

---

## Weekly OSM Data Refresh (Cron)
Add a cron job to `/etc/cron.weekly/laze-osm-update`:
```bash
#!/bin/bash
cd /opt/laze/infra
./scripts/setup-iraq.sh
docker compose restart osrm-iraq
```

---

## Analysis of OpenStreetMap Data Quality & Gaps in Iraq (تحليل جودة بيانات الخرائط في العراق)

Based on our analysis of Iraq OpenStreetMap data:
1. **Inter-governorate Highways (طرق المرور السريع بين المحافظات)**:
   - High coverage on Highway 1 (Baghdad - Basra) and Highway 2 (Baghdad - Erbil/Kirkuk).
   - Speeds and lane counts are well mapped, but road surface quality and temporary construction detours lack real-time tagging (covered by Laze's community hazard reports).
2. **Baghdad & Major Cities (المدن الكبرى)**:
   - High street geometry fidelity in central Baghdad (Karkh and Rusafa), Erbil, Basra, Sulaymaniyah, and Najaf.
   - **Gaps identified**:
     - Turn restrictions and one-way alleys in older historic districts.
     - Security checkpoints (سيطرات) are dynamic in real life and often omitted in static OSM; Laze's Supabase checkpoint reporting directly solves this gap.
     - Roundabouts (فلكات) in peripheral towns occasionally lack explicit exit numbering tags.
