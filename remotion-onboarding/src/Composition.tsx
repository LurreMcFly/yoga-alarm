import {
  AbsoluteFill,
  Composition,
  Easing,
  Img,
  Interactive,
  interpolate,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import {Scene3PhonePlacement} from "./Scene3PhonePlacement";

const SoundWave: React.FC<{delay: number; size: number}> = ({delay, size}) => {
  const frame = useCurrentFrame();
  const cycle = (frame - delay + 150) % 30;

  return (
    <div
      style={{
        position: "absolute",
        left: 878 - size / 2,
        top: 665 - size / 2,
        width: size,
        height: size,
        border: "9px solid rgba(255, 183, 63, 0.95)",
        borderRadius: "50%",
        opacity: interpolate(cycle, [0, 5, 24, 29], [0, 0.8, 0.22, 0], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
          easing: Easing.bezier(0.16, 1, 0.3, 1),
        }),
        scale: interpolate(cycle, [0, 29], [0.75, 1.25], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
          easing: Easing.bezier(0.16, 1, 0.3, 1),
          output: "perceptual-scale",
        }),
        maskImage:
          "linear-gradient(90deg, transparent 0%, transparent 38%, #000 54%, #000 100%)",
        filter: "drop-shadow(0 0 10px rgba(255, 167, 38, 0.38))",
      }}
    />
  );
};

const FloatingNote: React.FC<{
  delay: number;
  left: number;
  top: number;
  symbol: string;
}> = ({delay, left, top, symbol}) => {
  const frame = useCurrentFrame();
  const localFrame = (frame - delay + 150) % 50;

  return (
    <div
      style={{
        position: "absolute",
        left,
        top,
        color: "#7a3c1d",
        fontFamily: "Georgia, serif",
        fontSize: 38,
        fontWeight: 700,
        opacity: interpolate(localFrame, [0, 7, 36, 49], [0, 0.88, 0.55, 0], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
          easing: Easing.bezier(0.16, 1, 0.3, 1),
        }),
        translate: interpolate(
          localFrame,
          [0, 49],
          ["0px 22px", "12px -34px"],
          {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          },
        ),
        rotate: interpolate(localFrame, [0, 25, 49], ["-8deg", "5deg", "-2deg"], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
          easing: Easing.bezier(0.45, 0, 0.55, 1),
        }),
        textShadow: "0 3px 10px rgba(255, 247, 224, 0.7)",
      }}
    >
      {symbol}
    </div>
  );
};

const DustMote: React.FC<{
  delay: number;
  left: number;
  top: number;
  size: number;
}> = ({delay, left, top, size}) => {
  const frame = useCurrentFrame();
  const localFrame = (frame + delay) % 75;

  return (
    <div
      style={{
        position: "absolute",
        left,
        top,
        width: size,
        height: size,
        borderRadius: "50%",
        backgroundColor: "rgba(255, 238, 190, 0.85)",
        opacity: interpolate(localFrame, [0, 15, 56, 74], [0, 0.75, 0.42, 0], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
          easing: Easing.bezier(0.16, 1, 0.3, 1),
        }),
        translate: interpolate(
          localFrame,
          [0, 74],
          ["0px 24px", "20px -36px"],
          {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.45, 0, 0.55, 1),
          },
        ),
        filter: "blur(1px)",
      }}
    />
  );
};

export const WakeUpScene: React.FC = () => {
  const frame = useCurrentFrame();
  const {durationInFrames} = useVideoConfig();

  return (
    <AbsoluteFill style={{backgroundColor: "#eec7ac", overflow: "hidden"}}>
      <Img
        name="Wake-up illustration"
        src={staticFile("scene-2-wake-up.png")}
        style={{
          position: "absolute",
          inset: -24,
          width: 1128,
          height: 1128,
          objectFit: "cover",
          scale: interpolate(
            frame,
            [0, 75, durationInFrames - 1],
            [1.01, 1.045, 1.01],
            {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.45, 0, 0.55, 1),
            output: "perceptual-scale",
            },
          ),
          translate: interpolate(
            frame,
            [0, 75, durationInFrames - 1],
            ["0px 0px", "-7px -5px", "0px 0px"],
            {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.bezier(0.45, 0, 0.55, 1),
            },
          ),
          filter: `brightness(${interpolate(frame, [0, 60, 115, 149], [0.9, 1.04, 1.04, 0.9], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          })}) saturate(1.03)`,
        }}
      />

      <Interactive.Div
        name="Cool dawn tint"
        style={{
          position: "absolute",
          inset: 0,
          background:
            "linear-gradient(135deg, rgba(74, 103, 170, 0.36) 0%, rgba(116, 99, 158, 0.2) 42%, rgba(255, 205, 148, 0) 78%)",
          opacity: interpolate(frame, [0, 60, 115, 149], [0.6, 0.08, 0.08, 0.6], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      />

      <Interactive.Div
        name="Sunrise glow"
        style={{
          position: "absolute",
          inset: 0,
          background:
            "radial-gradient(circle at 13% 35%, rgba(255, 226, 142, 0.72) 0%, rgba(255, 196, 113, 0.26) 23%, rgba(255, 176, 102, 0) 54%)",
          opacity: interpolate(frame, [0, 60, 115, 149], [0.34, 0.96, 0.96, 0.34], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      />

      <Interactive.Div
        name="Phone alarm glow"
        style={{
          position: "absolute",
          left: 820,
          top: 585,
          width: 190,
          height: 245,
          borderRadius: 50,
          background:
            "radial-gradient(circle, rgba(255, 191, 71, 0.34) 0%, rgba(255, 174, 45, 0.13) 46%, rgba(255, 174, 45, 0) 72%)",
          opacity: interpolate(frame % 30, [0, 15, 29], [0.3, 0.95, 0.3], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.45, 0, 0.55, 1),
          }),
          scale: interpolate(frame % 30, [0, 15, 29], [0.92, 1.06, 0.92], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.45, 0, 0.55, 1),
            output: "perceptual-scale",
          }),
          filter: "blur(10px)",
        }}
      />

      <SoundWave delay={0} size={138} />
      <SoundWave delay={10} size={188} />
      <SoundWave delay={20} size={238} />

      <FloatingNote delay={4} left={884} top={565} symbol="♪" />
      <FloatingNote delay={26} left={946} top={530} symbol="♫" />
      <FloatingNote delay={44} left={842} top={610} symbol="♪" />

      <DustMote delay={5} left={118} top={344} size={9} />
      <DustMote delay={29} left={202} top={276} size={7} />
      <DustMote delay={57} left={300} top={388} size={11} />
      <DustMote delay={74} left={414} top={224} size={6} />

      <Interactive.Div
        name="Stretch accent"
        style={{
          position: "absolute",
          left: 96,
          top: 44,
          width: 120,
          height: 120,
          borderTop: "7px solid rgba(255, 210, 111, 0.82)",
          borderLeft: "7px solid rgba(255, 210, 111, 0.82)",
          borderRadius: "60px 0 0 0",
          opacity: interpolate(frame, [18, 28, 48, 58], [0, 0.8, 0.8, 0], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
          scale: interpolate(frame, [18, 58], [0.8, 1.08], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
            output: "perceptual-scale",
          }),
          rotate: "-10deg",
        }}
      />

      <AbsoluteFill
        style={{
          boxShadow: "inset 0 0 90px rgba(87, 46, 55, 0.16)",
          pointerEvents: "none",
        }}
      />
    </AbsoluteFill>
  );
};

export const RemotionCompositions: React.FC = () => {
  return (
    <>
      <Composition
        id="Scene2WakeUp"
        component={WakeUpScene}
        durationInFrames={150}
        fps={30}
        width={1080}
        height={1080}
      />
      <Composition
        id="Scene3PhonePlacement"
        component={Scene3PhonePlacement}
        durationInFrames={180}
        fps={30}
        width={1080}
        height={1080}
      />
    </>
  );
};
