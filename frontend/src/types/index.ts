export type RoleId = "admin" | "operator" | "new_user" | string;

export type PermissionKey =
  | "dashboard:view"
  | "contract:draft"
  | "contract:countersign"
  | "contract:finalize"
  | "contract:approve"
  | "contract:sign"
  | "query:info"
  | "query:process"
  | "base:contract"
  | "base:customer"
  | "system:assign"
  | "system:user"
  | "system:role"
  | "system:permission"
  | "system:log";

export type User = {
  id: string;
  name: string;
  password: string;
  email?: string;
  roleIds: RoleId[];
};

export type Role = {
  id: string;
  name: string;
  description: string;
  permissions: PermissionKey[];
};

export type Customer = {
  id: string;
  name: string;
  tel: string;
  address: string;
  fax?: string;
  email?: string;
  bank?: string;
  account?: string;
  remark?: string;
};

export type ContractStatus =
  | "drafting"
  | "countersigning"
  | "finalizing"
  | "approving"
  | "signing"
  | "completed"
  | "rejected";

export type Attachment = {
  name: string;
  type: string;
  path: string;
};

export type AiReview = {
  contract_summary: {
    contract_title: string;
    contract_type: string;
    party_a: string;
    party_b: string;
    subject_matter: string;
    total_amount: string;
    payment_terms: string;
    performance_period: string;
    effective_date: string;
    termination_date: string;
    dispute_resolution: string;
  };
  risk_alerts: Array<{
    risk_id: number;
    risk_category: string;
    risk_level: "高" | "中" | "低" | string;
    clause_reference: string;
    risk_description: string;
    suggestion: string;
  }>;
  overall_assessment: {
    overall_risk_level: "高" | "中" | "低" | string;
    missing_clauses: string[];
    summary: string;
  };
};

export type Contract = {
  id: string;
  num: string;
  name: string;
  customerId: string;
  beginTime: string;
  endTime: string;
  content: string;
  drafterId: string;
  createdAt: string;
  status: ContractStatus;
  aiReview?: string | AiReview | null;
  attachments?: Attachment[];
};

export type ProcessType = "countersign" | "finalize" | "approve" | "sign";
export type ProcessState = "pending" | "done" | "rejected";

export type ContractProcess = {
  id: string;
  contractId: string;
  type: ProcessType;
  userId: string;
  state: ProcessState;
  content?: string;
  createdAt?: string;
  time?: string;
};

export type SignRecord = {
  id: string;
  contractId: string;
  signDate: string;
  method: string;
  remark?: string;
  signerName?: string;
  signatureDataUrl?: string;
};

export type ContractVersion = {
  id: string;
  contractId: string;
  versionNo: number;
  num: string;
  name: string;
  customerId: string;
  beginTime: string;
  endTime: string;
  content: string;
  drafterId: string;
  approverId: string;
  approvalResult: "approved" | "rejected" | string;
  approvalOpinion: string;
  createdAt: string;
  attachments?: Attachment[];
};

export type Log = {
  id: string;
  userName: string;
  content: string;
  time: string;
};

export type Session = {
  userId: string;
  userName: string;
};
