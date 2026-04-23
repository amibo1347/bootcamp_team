export type Role = "ADMIN" | "USER";
export type Status = "WAIT" | "JOIN" | "LEAVE" | "REJECT";

export interface UserInfo {
  memberId: number;
  loginId: string;
  name: string;
  role: Role;
  companyId: number;
  companyName: string;
}

export interface LoginResponse {
  token: string;
  expiresIn: number;
  user: UserInfo;
}

export interface CompanyVerifyResponse {
  verified: boolean;
  companyId: number | null;
  logoPath: string | null;
}

export interface MemberSummary {
  memberId: number;
  loginId: string;
  name: string;
  email: string | null;
  phone: string | null;
  role: Role;
  status: Status;
  createdAt: string | null;
  acceptedAt: string | null;
  deptId: number | null;
  deptName: string | null;
  positionId: number | null;
  positionName: string | null;
}

export interface DeptSummary {
  deptId: number;
  deptName: string;
  deptCode: string;
}

export interface PositionSummary {
  positionId: number;
  positionName: string;
}

export interface ApiError {
  error: string;
  code?: string;
}
