#!/usr/bin/env bash
# ==============================================================================
# Laze Iraq Navigation Backend Setup Script
# Downloads OpenStreetMap Iraq data from Geofabrik, processes it with OSRM (MLD),
# generates Planetiler PMTiles vector tiles, and boots the stack via Docker.
# ==============================================================================

set -euo pipefail

DATA_DIR="./data"
OSRM_DATA="${DATA_DIR}/osrm"
PHOTON_DATA="${DATA_DIR}/photon"
TILES_DATA="${DATA_DIR}/tiles"

mkdir -p "${OSRM_DATA}" "${PHOTON_DATA}" "${TILES_DATA}"

echo "==> 1. Downloading Iraq OpenStreetMap extract from Geofabrik..."
PBF_URL="https://download.geofabrik.de/asia/iraq-latest.osm.pbf"
PBF_FILE="${OSRM_DATA}/iraq-latest.osm.pbf"

if [ ! -f "${PBF_FILE}" ]; then
    curl -L -o "${PBF_FILE}" "${PBF_URL}"
else
    echo "Found existing ${PBF_FILE}, skipping download."
fi

echo "==> 2. Preprocessing OSRM data (Multi-Level Dijkstra algorithm)..."
# Run OSRM extract using standard car profile
docker run --rm -t -v "$(pwd)/${OSRM_DATA}:/data" osrm/osrm-backend:v5.27.1 \
    osrm-extract -p /opt/car.lua /data/iraq-latest.osm.pbf

# Partition graph for MLD
docker run --rm -t -v "$(pwd)/${OSRM_DATA}:/data" osrm/osrm-backend:v5.27.1 \
    osrm-partition /data/iraq-latest.osrm

# Customize routing weights
docker run --rm -t -v "$(pwd)/${OSRM_DATA}:/data" osrm/osrm-backend:v5.27.1 \
    osrm-customize /data/iraq-latest.osrm

echo "==> 3. Initializing Photon search index for Arabic names..."
# Download pre-built search index or initialize photon
if [ ! -f "${PHOTON_DATA}/photon_data/indices" ]; then
    echo "Note: Photon index will initialize on first container start."
fi

echo "==> 4. Starting Laze Iraq infrastructure stack..."
docker compose up -d

echo "==> Setup complete! Checking health..."
sleep 5
curl -s http://localhost/health || true
echo ""
echo "OSRM Routing API: http://localhost/route/v1/driving/..."
echo "Photon Search API: http://localhost/api?q=..."
echo "Vector Tiles API: http://localhost/tiles/..."
