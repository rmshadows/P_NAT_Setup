#!/bin/bash

mkdir RELEASE
#### FRP
cd FRP
python3 frp_release.py
mv FRP_AUTO ../RELEASE
cd ..
#### ndp(.Net)
cd "ndp(.Net)"
python3 ndp_release.py
mv NDP_AUTO ../RELEASE
cd ..
#### Tight VNC
cd TightVNC
python3 tightvnc_release.py
mv TIGHTVNC_AUTO ../RELEASE
cd ..
#### ZeroTier
cd ZeroTier
python3 zerotier_release.py
mv ZEROTIER_AUTO ../RELEASE
cd ..
echo ""
echo ""
echo "Success."



