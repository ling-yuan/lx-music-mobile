import { PixelRatio, Platform, requireNativeComponent, StyleSheet, View, type ViewProps, type TextStyle } from 'react-native'
import Text, { AnimatedColorText } from '@/components/common/Text'
import { type Line } from '@/plugins/lyric'
import { setSpText } from '@/utils/pixelRatio'
import playerState from '@/store/player/state'

interface NativeProps extends ViewProps {
  text: string
  lyricTime: number
  active: boolean
  playedColor: string
  fontSize: number
  lineHeight: number
  align: string
}

const NativeKaraoke = Platform.OS == 'android' ? requireNativeComponent<NativeProps>('LXKaraokeText') : null

export default ({ line, active, color, opacity, size, style }: {
  line: Line
  active: boolean
  color: string
  opacity: number
  size: number
  style: TextStyle
}) => {
  const karaoke = active && NativeKaraoke && !!playerState.musicInfo.lxlrc
  return (
    <View>
      {karaoke ? <Text style={{ ...style, opacity: 0 }} textBreakStrategy="simple" size={size}>{line.text}</Text> : <AnimatedColorText style={style} textBreakStrategy="simple" color={color} opacity={opacity} size={size}>{line.text}</AnimatedColorText>}
      {karaoke && NativeKaraoke ? (
        <NativeKaraoke
          pointerEvents="none"
          style={StyleSheet.absoluteFill}
          text={line.text}
          lyricTime={line.time}
          active={active}
          playedColor={color}
          fontSize={setSpText(size) * PixelRatio.get() * PixelRatio.getFontScale()}
          lineHeight={Number(style.lineHeight) * PixelRatio.get() * PixelRatio.getFontScale()}
          align={style.textAlign ?? 'left'}
        />
      ) : null}
    </View>
  )
}
