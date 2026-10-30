import { NativeEventEmitter, NativeModules } from 'react-native'
import BackgroundTimer from 'react-native-background-timer'
import { fetchData } from '@/core/init/userApi/request'
import { requestMsg } from '@/utils/message'

const { UserApiModule } = NativeModules

let loadScriptInfo: LX.UserApi.UserApiInfo | null = null
export const loadScript = (info: LX.UserApi.UserApiInfo & { script: string }) => {
  loadScriptInfo = info
  UserApiModule.loadScript({
    id: info.id,
    name: info.name,
    description: info.description,
    version: info.version ?? '',
    author: info.author ?? '',
    homepage: info.homepage ?? '',
    script: info.script,
  })
}

export interface SendResponseParams {
  requestKey: string
  error: string | null
  response: {
    statusCode: number
    statusMessage: string
    headers: Record<string, string>
    body: any
  } | null
}
export interface SendActions {
  request: LX.UserApi.UserApiRequestParams
  response: SendResponseParams
}
export const sendAction = <T extends keyof SendActions>(action: T, data: SendActions[T]) => {
  UserApiModule.sendAction(action, JSON.stringify(data))
}

// export const clearAppCache = CacheModule.clearAppCache as () => Promise<void>

export interface InitParams {
  status: boolean
  errorMessage: string
  info: LX.UserApi.UserApiInfo
}

export interface ResponseParams {
  status: boolean
  errorMessage?: string
  requestKey: string
  result: any
}
export interface UpdateInfoParams {
  name: string
  log: string
  updateUrl: string
}
export interface RequestParams {
  requestKey: string
  url: string
  options: {
    method: string
    data: any
    timeout: number
    headers: any
    binary: boolean
  }
}
export type CancelRequestParams = string

export interface Actions {
  init: InitParams
  request: RequestParams
  cancelRequest: CancelRequestParams
  response: ResponseParams
  showUpdateAlert: UpdateInfoParams
  log: string
}
export type ActionsEvent = { [K in keyof Actions]: { action: K, data: Actions[K] } }[keyof Actions]

export const onScriptAction = (handler: (event: ActionsEvent) => void, sessionId?: string): () => void => {
  // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
  const eventEmitter = new NativeEventEmitter(UserApiModule)
  const eventListener = eventEmitter.addListener('api-action', event => {
    if ((event.sessionId ?? undefined) !== sessionId) return
    if (event.data) event.data = JSON.parse(event.data as string)
    if (!sessionId && event.action == 'init') {
      if (event.data.info) event.data.info = { ...loadScriptInfo, ...event.data.info }
      else event.data.info = { ...loadScriptInfo }
    } else if (!sessionId && event.action == 'showUpdateAlert') {
      if (!loadScriptInfo?.allowShowUpdateAlert) return
    }
    handler(event as ActionsEvent)
  })

  return () => {
    eventListener.remove()
  }
}

export const destroy = () => {
  UserApiModule.destroy()
}

export const getFallbackMusicUrl = async(info: LX.UserApi.UserApiInfo & { script: string }, musicInfo: LX.Music.MusicInfo, qualitys: LX.Quality[], signal?: AbortSignal): Promise<{ url: string, type: LX.Quality }> => {
  if (signal?.aborted) throw new Error(requestMsg.cancelRequest)
  const sessionId = `fallback__${Date.now()}_${Math.random().toString().substring(2)}`
  const requests = new Map<string, { request: Promise<any>, abort: () => void }>()
  let closed = false
  let removeListener = () => {}
  let cancel = () => {}
  let timeout: number | undefined
  const send = <T extends keyof SendActions>(action: T, data: SendActions[T]) => {
    if (!closed) UserApiModule.sendFallbackAction(sessionId, action, JSON.stringify(data))
  }
  try {
    return await new Promise<{ url: string, type: LX.Quality }>((resolve, reject) => {
      let quality: LX.Quality | undefined
      let requested = false
      cancel = () => { reject(new Error(requestMsg.cancelRequest)) }
      global.state_event.on('apiSourceUpdated', cancel)
      signal?.addEventListener('abort', cancel)
      timeout = BackgroundTimer.setTimeout(() => { reject(new Error('request timeout')) }, 20_000)
      removeListener = onScriptAction((event) => {
        switch (event.action) {
          case 'init': {
            if (requested) return
            const source = event.data?.info?.sources?.[musicInfo.source]
            // Capability data comes from the script, not from TypeScript types.
            if (!event.data?.status || source?.type != 'music' || !Array.isArray(source.actions) || !Array.isArray(source.qualitys) || !source.actions.includes('musicUrl')) {
              reject(new Error(event.data?.errorMessage || 'source not supported'))
              return
            }
            quality = qualitys.find(item => source.qualitys.includes(item))
            if (!quality) {
              reject(new Error('source not supported'))
              return
            }
            requested = true
            send('request', {
              requestKey: sessionId,
              data: { source: musicInfo.source, action: 'musicUrl', info: { type: quality, musicInfo } },
            })
            break
          }
          case 'request': {
            const { requestKey, url, options } = event.data
            let request: ReturnType<typeof fetchData>
            try {
              request = fetchData(url, options)
            } catch (err) {
              reject(err)
              return
            }
            requests.set(requestKey, request)
            void request.request.then(response => {
              send('response', { requestKey, error: null, response })
            }).catch((err: Error) => {
              send('response', { requestKey, error: err.message, response: null })
            }).finally(() => { requests.delete(requestKey) })
            break
          }
          case 'cancelRequest':
            requests.get(event.data)?.abort()
            requests.delete(event.data)
            break
          case 'response':
            if (event.data.requestKey != sessionId || !quality) return
            if (event.data.status && typeof event.data.result?.data?.url == 'string' && event.data.result.data.url) {
              resolve({ url: event.data.result.data.url, type: quality })
            } else reject(new Error(event.data.errorMessage ?? 'failed'))
            break
          default:
            break
        }
      }, sessionId)
      UserApiModule.loadFallbackScript(sessionId, { ...info, allowShowUpdateAlert: false })
    })
  } finally {
    closed = true
    if (timeout !== undefined) BackgroundTimer.clearTimeout(timeout)
    global.state_event.off('apiSourceUpdated', cancel)
    signal?.removeEventListener('abort', cancel)
    removeListener()
    for (const request of requests.values()) request.abort()
    UserApiModule.destroyFallbackScript(sessionId)
  }
}
