import axios from 'axios'
import http from './request'
import type { PageResult } from './system'
import { doneProgress, startProgress } from '@/utils/progress'

export interface FileItem {
  id: number
  bizType: string
  bizId?: number
  bucketName: string
  objectKey: string
  originalName: string
  contentType: string
  fileSize: number
  fileExt?: string
  uploaderId: number
  uploaderName?: string
  createdAt?: string
}

export interface FileShareItem {
  id: number
  fileId: number
  fileName?: string
  shareCode: string
  expireTime?: string
  createdBy: number
  creatorName?: string
  createdAt?: string
}

export function filePageApi(params: { page?: number; size?: number; keyword?: string; bizType?: string; bizId?: number }) {
  return http.get<unknown, PageResult<FileItem>>('/files/page', { params })
}

export function uploadFileApi(file: File, data: { bizType?: string; bizId?: number }) {
  const formData = new FormData()
  formData.append('file', file)
  if (data.bizType) formData.append('bizType', data.bizType)
  if (data.bizId !== undefined) formData.append('bizId', String(data.bizId))
  return http.post<unknown, FileItem>('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function updateFileApi(id: number, data: { bizType?: string; bizId?: number; originalName?: string }) {
  return http.put<unknown, FileItem>(`/files/${id}`, data)
}

export function deleteFileApi(id: number) {
  return http.delete<unknown, null>(`/files/${id}`)
}

export function createFileShareApi(data: { fileId: number; expireDays?: number }) {
  return http.post<unknown, FileShareItem>('/file-shares', data)
}

export function fileSharePageApi(params: { page?: number; size?: number; keyword?: string }) {
  return http.get<unknown, PageResult<FileShareItem>>('/file-shares/page', { params })
}

export function previewFileBlobApi(id: number) {
  return blobRequest(`/files/${id}/preview`)
}

export function downloadFileBlobApi(id: number) {
  return blobRequest(`/files/${id}/download`)
}

function blobRequest(url: string) {
  const token = localStorage.getItem('teamflow_access_token')
  startProgress()
  return axios
    .get<Blob>((import.meta.env.VITE_API_BASE_URL || '/api') + url, {
      responseType: 'blob',
      headers: token ? { Authorization: `Bearer ${token}` } : undefined
    })
    .finally(() => doneProgress())
}
