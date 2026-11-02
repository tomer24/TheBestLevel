#!/usr/bin/env bash
set -euo pipefail
TARGET=/tmp/nexora-client
mkdir -p "$TARGET"
base64 -d nexora-build/nexora-v2-source.tgz.b64 | tar -xz -C "$TARGET"

# Convert the original interdisciplinary named Java packages to NEXORA.
for SROOT in "$TARGET/src/main/java" "$TARGET/glow/src/main/java"; do
  OLD="$SROOT/dev/tomer/modhub"
  NEW="$SROOT/net/nexora/client"
  test -d "$OLD"
  mkdir -p "$NEW"
  mv "$OLD"/*.java "$NEW/"
  rm -rf "$SROOT/dev"
  find "$NEW" -name '*.java' -print0 | xargs -0 sed -i \
    -e 's/dev\.tomer\.modhub/net.nexora.client/g' \
    -e 's/TomerModHub/NexoraClient/g' \
    -e 's/tomermodhub/nexora/g'
done
mv "$TARGET/src/main/java/net/nexora/client/TomerModHub.java" "$TARGET/src/main/java/net/nexora/client/NexoraClient.java"
cp "$TARGET/src/main/java/net/nexora/client/GlowBridge.java" "$TARGET/glow/src/main/java/net/nexora/client/GlowBridge.java"
sed -i -e 's/dev\.tomer\.modhub\.TomerModHub/net.nexora.client.NexoraClient/g' "$TARGET/src/main/resources/fabric.mod.json"

python3 - <<'PY'
from pathlib import Path
root = Path('/tmp/nexora-client')
features = root/'glow/src/main/java/net/nexora/client/NexoraFeatures.java'
s = features.read_text()
s=s.replace('private static int ticks;', 'private static int ticks;\n    private static boolean swimWasHeld;')
a='''            if(on(ModuleBridge.SWIM)&&mc.options.keyUp.isDown()&&mc.player.isInWaterOrBubble()){
                mc.options.keyJump.setDown(true);
                mc.player.setSprinting(true);
            }'''
b='''            boolean shouldSwim=on(ModuleBridge.SWIM)&&mc.options.keyUp.isDown()&&mc.player.isInWaterOrBubble();
            if(shouldSwim){
                mc.options.keyJump.setDown(true);
                mc.player.setSprinting(true);
                swimWasHeld=true;
            } else if(swimWasHeld){
                mc.options.keyJump.setDown(key(mc,GLFW.GLFW_KEY_SPACE));
                swimWasHeld=false;
            }'''
if a not in s: raise Exception('Old swim code missing, refusing silent patch')
features.write_text(s.replace(a,b))
world = root/'glow/src/main/java/net/nexora/client/WorldGlowRenderer.java'
s=world.read_text()
s=s.replace('point.x-r,point.y-r,point.z-r,','(float)(point.x-r),(float)(point.y-r),(float)(point.z-r),')
s=s.replace('point.x+r,point.y+r,point.z+r,','(float)(point.x+r),(float)(point.y+r),(float)(point.z+r),')
s=s.replace('bb.minX-pad,bb.minY-pad,bb.minZ-pad,bb.maxX+pad,bb.maxY+pad,bb.maxZ+pad,','(float)(bb.minX-pad),(float)(bb.minY-pad),(float)(bb.minZ-pad),\n            (float)(bb.maxX+pad),(float)(bb.maxY+pad),(float)(bb.maxZ+pad),')
world.write_text(s)
nav = root/'src/main/java/net/nexora/client/BuiltInNavigator.java'
s=nav.read_text()
s=s.replace('heuristic(x,z,goalX+.5,goalZ+.5)','Math.hypot(x-(goalX+.5),z-(goalZ+.5))')
s=s.replace('heuristic(x,z,next.x()+.5,next.z()+.5)','Math.hypot(x-(next.x()+.5),z-(next.z()+.5))')
nav.write_text(s)
s=features.read_text()
s=s.replace('import net.minecraft.client.ParticleStatus;', 'import net.minecraft.server.level.ParticleStatus;')
s=s.replace('mc.getWindow().getWindow()', 'mc.getWindow().handle()')
s=s.replace('mc.player.isInWaterOrBubble()', 'mc.player.isInWater()')
features.write_text(s)
PY

test -f "$TARGET/src/main/java/net/nexora/client/NexoraClient.java"
test -f "$TARGET/glow/src/main/java/net/nexora/client/NexoraFeatures.java"
echo "NEXORA source ready. Main & visual source files:"
find "$TARGET" -name '*.java' -type f | sort
