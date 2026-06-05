export type PreviewMode = 'image' | 'pdf' | 'text' | 'word' | 'excel' | 'pptx' | 'none'

export interface PreviewResult {
  mode: PreviewMode
  /** 图片/PDF 使用的 ObjectURL，需在释放时 revoke */
  url?: string
  /** 纯文本预览内容 */
  text?: string
  /** Word/Excel/PPT 交给 @vue-office 渲染的二进制内容 */
  src?: ArrayBuffer
}

const TEXT_EXTS = ['md', 'json', 'xml', 'sql', 'txt', 'log', 'yml', 'yaml', 'csv']

/**
 * 根据文件 blob、内容类型、扩展名解析出最合适的预览方式。
 * 高保真支持：图片、PDF、纯文本、Word(docx)、Excel(xlsx)、PPT(pptx)。
 * 旧二进制格式(doc/xls/ppt)无可靠的浏览器渲染方案，返回 none 由调用方提示下载。
 */
export async function resolvePreview(blob: Blob, contentType?: string, fileExt?: string): Promise<PreviewResult> {
  const ext = (fileExt || '').toLowerCase()
  const ct = (contentType || '').toLowerCase()

  if (ct.startsWith('image/')) {
    return { mode: 'image', url: URL.createObjectURL(blob) }
  }
  if (ct === 'application/pdf' || ext === 'pdf') {
    return { mode: 'pdf', url: URL.createObjectURL(blob) }
  }
  if (ext === 'docx') {
    return { mode: 'word', src: await blob.arrayBuffer() }
  }
  if (ext === 'xlsx') {
    return { mode: 'excel', src: await blob.arrayBuffer() }
  }
  if (ext === 'pptx') {
    return { mode: 'pptx', src: await blob.arrayBuffer() }
  }
  if (ct.startsWith('text/') || TEXT_EXTS.includes(ext)) {
    return { mode: 'text', text: await blob.text() }
  }
  return { mode: 'none' }
}
