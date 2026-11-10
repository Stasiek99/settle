export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface User {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
}

export interface Group {
  id: string;
  name: string;
  currency: string;
  createdBy: string;
  createdAt: string;
  version: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RegisterRequest {
  email: string;
  displayName: string;
  password: string;
}
