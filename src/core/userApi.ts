import { action, state } from '@/store/userApi'
import { addUserApi, getUserApiScript, removeUserApi as removeUserApiFromStore, setUserApiAllowShowUpdateAlert as setUserApiAllowShowUpdateAlertFromStore } from '@/utils/data'
import { destroy, getFallbackMusicUrl, loadScript } from '@/utils/nativeModules/userApi'
import { log as writeLog } from '@/utils/log'
import settingState from '@/store/setting/state'
import { requestMsg } from '@/utils/message'


export const setUserApi = async(apiId: string) => {
  global.lx.qualityList = {}
  setUserApiStatus(false, 'initing')

  const target = state.list.find(api => api.id === apiId)
  if (!target) throw new Error('api not found')
  const script = await getUserApiScript(target.id)
  loadScript({ ...target, script })
}

export const destroyUserApi = () => {
  destroy()
}

export const getOtherUserApiMusicUrl = async(musicInfo: LX.Music.MusicInfo, qualitys: LX.Quality[], apiId: string, onToggleApi?: (name: string) => void): Promise<{ url: string, type: LX.Quality }> => {
  if (!apiId.startsWith('user_api')) throw new Error('source not supported')
  // Snapshot the list so imports or removals do not reorder an in-flight fallback.
  const candidates = state.list.filter(api => api.id != apiId)
  const controller = new AbortController()
  const cancel = () => { controller.abort() }
  const checkCanceled = () => {
    if (controller.signal.aborted || settingState.setting['common.apiSource'] != apiId) throw new Error(requestMsg.cancelRequest)
  }
  global.app_event.on('musicToggled', cancel)
  global.app_event.on('stop', cancel)
  global.state_event.on('apiSourceUpdated', cancel)
  try {
    for (const api of candidates) {
      checkCanceled()
      try {
        const script = await getUserApiScript(api.id)
        checkCanceled()
        onToggleApi?.(api.name)
        const result = await getFallbackMusicUrl({ ...api, script }, musicInfo, qualitys, controller.signal)
        checkCanceled()
        return result
      } catch (err) {
        checkCanceled()
        if ((err as Error).message == requestMsg.cancelRequest) throw err
      }
    }
    throw new Error('all user apis failed')
  } finally {
    global.app_event.off('musicToggled', cancel)
    global.app_event.off('stop', cancel)
    global.state_event.off('apiSourceUpdated', cancel)
  }
}


export const setUserApiStatus: typeof action['setStatus'] = (status, message) => {
  action.setStatus(status, message)
}

export const setUserApiList: typeof action['setUserApiList'] = (list) => {
  action.setUserApiList(list)
}

export const importUserApi = async(script: string, importUrl?: string) => {
  const info = await addUserApi(script, importUrl)
  action.addUserApi(info)
}

export const removeUserApi = async(ids: string[]) => {
  const list = await removeUserApiFromStore(ids)
  action.setUserApiList(list)
}

export const setUserApiAllowShowUpdateAlert = async(id: string, enable: boolean) => {
  await setUserApiAllowShowUpdateAlertFromStore(id, enable)
  action.setUserApiAllowShowUpdateAlert(id, enable)
}

export const log = {
  r_info(...params: any[]) {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
    writeLog.info(...params)
  },
  r_warn(...params: any[]) {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
    writeLog.warn(...params)
  },
  r_error(...params: any[]) {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
    writeLog.error(...params)
  },
  log(...params: any[]) {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
    if (global.lx.isEnableUserApiLog) writeLog.info(...params)
  },
  info(...params: any[]) {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
    if (global.lx.isEnableUserApiLog) writeLog.info(...params)
  },
  warn(...params: any[]) {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
    if (global.lx.isEnableUserApiLog) writeLog.warn(...params)
  },
  error(...params: any[]) {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
    if (global.lx.isEnableUserApiLog) writeLog.error(...params)
  },
}
