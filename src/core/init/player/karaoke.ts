import BackgroundTimer from 'react-native-background-timer'
import { AppState, Platform } from 'react-native'
import { getPosition } from '@/plugins/player'
import playerState from '@/store/player/state'
import settingState from '@/store/setting/state'
import { pause, handlePlay } from '@/core/lyric'
import { syncKaraoke } from '@/utils/nativeModules/lyricDesktop'

export default () => {
  if (Platform.OS != 'android') return
  let playing = false
  let revision = 0
  let timer: number | null = null
  const sync = async(restartLines = false) => {
    const id = playerState.musicInfo.id
    const currentRevision = revision
    try {
      const position = await getPosition()
      if (currentRevision != revision || id != playerState.musicInfo.id) return
      const time = position * 1000
      await syncKaraoke(time, playing, settingState.setting['player.playbackRate'])
      if (currentRevision != revision || id != playerState.musicInfo.id) return
      if (restartLines && playing) handlePlay(time)
    } catch {}
  }
  const freeze = (resync = true) => {
    playing = false
    revision++
    if (timer != null) BackgroundTimer.clearInterval(timer)
    timer = null
    pause()
    if (resync) void sync()
  }
  const resume = () => {
    playing = true
    revision++
    if (timer != null) BackgroundTimer.clearInterval(timer)
    timer = BackgroundTimer.setInterval(() => { void sync() }, 1000)
    void sync(true)
  }
  const reset = () => {
    freeze(false)
    void syncKaraoke(0, false, settingState.setting['player.playbackRate'])
  }
  global.app_event.on('playerPlaying', resume)
  global.app_event.on('playerWaiting', freeze)
  global.app_event.on('playerLoadstart', freeze)
  global.app_event.on('pause', freeze)
  global.app_event.on('error', freeze)
  global.app_event.on('stop', reset)
  global.app_event.on('musicToggled', reset)
  global.app_event.on('setProgress', () => { revision++ })
  global.app_event.on('lyricUpdated', () => { void sync(playing) })
  global.state_event.on('configUpdated', (keys) => {
    if (keys.includes('player.playbackRate')) {
      revision++
      void sync(playing)
    }
  })
  AppState.addEventListener('change', (state) => {
    if (state == 'active') void sync(playing)
  })
}
