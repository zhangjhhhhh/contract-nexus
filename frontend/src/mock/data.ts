import type { Contract, ContractProcess, Customer, Log, PermissionKey, Role, SignRecord, User } from "../types";

export const permissionGroups: { group: string; items: { key: PermissionKey; label: string }[] }[] = [
  {
    group: "合同管理",
    items: [
      { key: "contract:draft", label: "起草合同" },
      { key: "contract:countersign", label: "会签合同" },
      { key: "contract:finalize", label: "定稿合同" },
      { key: "contract:approve", label: "审批合同" },
      { key: "contract:sign", label: "签订合同" }
    ]
  },
  {
    group: "流程管理",
    items: [
      { key: "query:info", label: "合同信息查询" },
      { key: "query:process", label: "合同流程查询" },
      { key: "system:assign", label: "分配合同" }
    ]
  },
  {
    group: "用户管理",
    items: [{ key: "system:user", label: "用户管理" }]
  },
  {
    group: "角色管理",
    items: [{ key: "system:role", label: "角色管理" }]
  },
  {
    group: "功能操作",
    items: [
      { key: "dashboard:view", label: "工作台" },
      { key: "base:contract", label: "合同信息管理" }
    ]
  },
  {
    group: "权限管理",
    items: [{ key: "system:permission", label: "权限分配" }]
  },
  {
    group: "客户管理",
    items: [
      { key: "base:customer", label: "客户信息管理" },
      { key: "system:log", label: "日志管理" }
    ]
  }
];

const adminPermissions = permissionGroups.flatMap((group) => group.items.map((item) => item.key));
const operatorPermissions: PermissionKey[] = [
  "dashboard:view",
  "contract:draft",
  "contract:countersign",
  "contract:finalize",
  "contract:approve",
  "contract:sign",
  "query:info",
  "query:process",
  "base:contract",
  "base:customer"
];

const defaultUserEmail = "user@example.com";

export const initialRoles: Role[] = [
  {
    id: "admin",
    name: "管理员",
    description: "分配合同流转人员，维护用户、角色、权限与日志。",
    permissions: adminPermissions
  },
  {
    id: "operator",
    name: "合同操作员",
    description: "负责合同起草、会签、定稿、审批与签订。",
    permissions: operatorPermissions
  },
  {
    id: "new_user",
    name: "新用户",
    description: "注册后等待管理员授权。",
    permissions: []
  },
  {
    id: "finance",
    name: "财务复核",
    description: "参与审批、签订与合同查询。",
    permissions: ["dashboard:view", "contract:approve", "contract:sign", "query:info", "query:process"]
  }
];

export const initialUsers: User[] = [
  { id: "U001", name: "admin", password: "admin123", email: defaultUserEmail, roleIds: ["admin"] },
  { id: "U002", name: "operator", password: "operator123", email: defaultUserEmail, roleIds: ["operator"] },
  { id: "U003", name: "zhangmin", password: "operator123", email: defaultUserEmail, roleIds: ["operator"] },
  { id: "U004", name: "liwei", password: "operator123", email: defaultUserEmail, roleIds: ["operator"] },
  { id: "U005", name: "wangyan", password: "finance", email: defaultUserEmail, roleIds: ["finance"] },
  { id: "U006", name: "chenhao", password: "operator123", email: defaultUserEmail, roleIds: ["operator", "finance"] },
  { id: "U007", name: "newuser", password: "newuser123", email: defaultUserEmail, roleIds: ["new_user"] },
  { id: "U008", name: "sunqi", password: "operator123", email: defaultUserEmail, roleIds: ["operator"] }
];

export const initialCustomers: Customer[] = [
  {
    id: "C001",
    name: "华南智造有限公司",
    tel: "0755-88990011",
    address: "深圳市南山区科技园科苑路 88 号",
    fax: "0755-88990012",
    email: "contact@hnzz.example",
    bank: "招商银行深圳分行",
    account: "755900010001",
    remark: "重点客户，年度维保合作。"
  },
  {
    id: "C002",
    name: "星河供应链集团",
    tel: "020-66881230",
    address: "广州市天河区珠江新城华夏路 16 号",
    fax: "020-66881231",
    email: "service@xinghe.example",
    bank: "中国银行广州分行",
    account: "440100020002",
    remark: "物流服务框架客户。"
  },
  {
    id: "C003",
    name: "北辰数据科技股份有限公司",
    tel: "010-56667788",
    address: "北京市海淀区中关村东路 9 号",
    email: "legal@beichen.example",
    bank: "建设银行北京分行",
    account: "110100030003"
  },
  {
    id: "C004",
    name: "江南能源服务有限公司",
    tel: "025-77889900",
    address: "南京市建邺区江东中路 128 号",
    fax: "025-77889901",
    email: "office@jnenergy.example",
    bank: "工商银行南京分行",
    account: "320100040004"
  },
  {
    id: "C005",
    name: "海岳建筑工程有限公司",
    tel: "0571-88332211",
    address: "杭州市西湖区文三路 199 号",
    email: "business@haiyue.example",
    bank: "交通银行杭州分行",
    account: "330100050005",
    remark: "工程类合同需关注履约节点。"
  }
];

export const initialContracts: Contract[] = [
  {
    id: "K001",
    num: "HT-2026-001",
    name: "年度设备维护合同",
    customerId: "C001",
    beginTime: "2026-06-01",
    endTime: "2027-05-31",
    content: "约定生产设备巡检、故障响应、备件更换、季度复盘与验收标准。",
    drafterId: "U002",
    createdAt: "2026-05-01 09:20",
    status: "drafting",
    attachments: [{ name: "maintenance.doc", type: "doc", path: "/mock/maintenance.doc" }]
  },
  {
    id: "K002",
    num: "HT-2026-002",
    name: "物流服务框架合同",
    customerId: "C002",
    beginTime: "2026-07-01",
    endTime: "2027-06-30",
    content: "明确仓配服务范围、运费结算、异常处理、赔付标准与对账周期。",
    drafterId: "U002",
    createdAt: "2026-05-03 10:12",
    status: "countersigning"
  },
  {
    id: "K003",
    num: "HT-2026-003",
    name: "数据平台采购合同",
    customerId: "C003",
    beginTime: "2026-06-15",
    endTime: "2027-06-14",
    content: "采购数据治理平台授权、部署服务、培训支持与售后保障。",
    drafterId: "U003",
    createdAt: "2026-05-04 14:35",
    status: "finalizing"
  },
  {
    id: "K004",
    num: "HT-2026-004",
    name: "新能源站点运维合同",
    customerId: "C004",
    beginTime: "2026-06-20",
    endTime: "2028-06-19",
    content: "覆盖站点巡检、远程监控、故障升级、备品备件和服务报告。",
    drafterId: "U004",
    createdAt: "2026-05-06 16:08",
    status: "approving"
  },
  {
    id: "K005",
    num: "HT-2026-005",
    name: "园区弱电施工合同",
    customerId: "C005",
    beginTime: "2026-06-10",
    endTime: "2026-09-30",
    content: "约定弱电施工范围、材料标准、里程碑验收与质保责任。",
    drafterId: "U003",
    createdAt: "2026-05-07 11:05",
    status: "signing"
  },
  {
    id: "K006",
    num: "HT-2026-006",
    name: "办公软件订阅合同",
    customerId: "C003",
    beginTime: "2026-05-15",
    endTime: "2027-05-14",
    content: "订阅办公协作套件账号、技术支持与年度续费条款。",
    drafterId: "U002",
    createdAt: "2026-04-26 13:18",
    status: "completed"
  },
  {
    id: "K007",
    num: "HT-2026-007",
    name: "仓储租赁补充协议",
    customerId: "C002",
    beginTime: "2026-05-20",
    endTime: "2026-12-31",
    content: "补充仓库面积、收费方式、安全责任与提前退租条件。",
    drafterId: "U004",
    createdAt: "2026-04-30 15:00",
    status: "rejected"
  },
  {
    id: "K008",
    num: "HT-2026-008",
    name: "安全巡检服务合同",
    customerId: "C004",
    beginTime: "2026-07-01",
    endTime: "2027-06-30",
    content: "提供安全巡检、整改建议、风险评估与季度汇报。",
    drafterId: "U008",
    createdAt: "2026-05-08 09:44",
    status: "drafting"
  },
  {
    id: "K009",
    num: "HT-2026-009",
    name: "智能终端采购合同",
    customerId: "C001",
    beginTime: "2026-07-10",
    endTime: "2026-10-10",
    content: "采购智能终端设备、交付验收、质保和违约责任。",
    drafterId: "U003",
    createdAt: "2026-05-10 10:30",
    status: "countersigning"
  },
  {
    id: "K010",
    num: "HT-2026-010",
    name: "数据中心机房改造合同",
    customerId: "C003",
    beginTime: "2026-08-01",
    endTime: "2026-12-20",
    content: "包含机房配电、消防、空调、监控与阶段验收。",
    drafterId: "U002",
    createdAt: "2026-05-11 17:12",
    status: "finalizing"
  },
  {
    id: "K011",
    num: "HT-2026-011",
    name: "企业培训服务合同",
    customerId: "C005",
    beginTime: "2026-06-05",
    endTime: "2026-08-05",
    content: "提供项目管理培训、课程材料、讲师服务和课后评估。",
    drafterId: "U008",
    createdAt: "2026-05-13 09:00",
    status: "approving"
  },
  {
    id: "K012",
    num: "HT-2026-012",
    name: "售后备件采购协议",
    customerId: "C001",
    beginTime: "2026-06-01",
    endTime: "2026-12-31",
    content: "约定备件目录、下单方式、交付周期和质量责任。",
    drafterId: "U004",
    createdAt: "2026-05-14 14:28",
    status: "signing"
  },
  {
    id: "K013",
    num: "HT-2026-013",
    name: "法律顾问服务合同",
    customerId: "C002",
    beginTime: "2026-01-01",
    endTime: "2026-12-31",
    content: "提供常年法律咨询、合同审阅、争议处理与合规培训。",
    drafterId: "U003",
    createdAt: "2025-12-20 10:20",
    status: "completed"
  },
  {
    id: "K014",
    num: "HT-2026-014",
    name: "工程监理服务合同",
    customerId: "C005",
    beginTime: "2026-04-01",
    endTime: "2026-10-31",
    content: "监理工程质量、安全、进度与资料归档。",
    drafterId: "U002",
    createdAt: "2026-03-10 12:42",
    status: "completed"
  },
  {
    id: "K015",
    num: "HT-2026-015",
    name: "市场推广合作合同",
    customerId: "C004",
    beginTime: "2026-05-01",
    endTime: "2026-07-31",
    content: "合作开展行业会议推广、物料制作、线索交付与费用结算。",
    drafterId: "U008",
    createdAt: "2026-04-18 16:36",
    status: "rejected"
  }
];

export const initialProcesses: ContractProcess[] = [
  { id: "P001", contractId: "K002", type: "countersign", userId: "U003", state: "pending" },
  { id: "P002", contractId: "K002", type: "countersign", userId: "U004", state: "pending" },
  { id: "P003", contractId: "K002", type: "approve", userId: "U005", state: "pending" },
  { id: "P004", contractId: "K002", type: "sign", userId: "U006", state: "pending" },
  { id: "P005", contractId: "K003", type: "countersign", userId: "U002", state: "done", content: "商务条款完整，建议补充交付验收表。", time: "2026-05-05 09:18" },
  { id: "P006", contractId: "K003", type: "countersign", userId: "U004", state: "done", content: "技术服务范围清晰，可以定稿。", time: "2026-05-05 11:26" },
  { id: "P007", contractId: "K003", type: "approve", userId: "U005", state: "pending" },
  { id: "P008", contractId: "K003", type: "sign", userId: "U006", state: "pending" },
  { id: "P009", contractId: "K004", type: "countersign", userId: "U002", state: "done", content: "运维响应时限已明确。", time: "2026-05-07 10:21" },
  { id: "P010", contractId: "K004", type: "approve", userId: "U005", state: "pending" },
  { id: "P011", contractId: "K004", type: "sign", userId: "U006", state: "pending" },
  { id: "P012", contractId: "K005", type: "countersign", userId: "U002", state: "done", content: "施工材料规格已确认。", time: "2026-05-08 10:40" },
  { id: "P013", contractId: "K005", type: "approve", userId: "U005", state: "done", content: "同意签订，注意验收资料归档。", time: "2026-05-09 15:10" },
  { id: "P014", contractId: "K005", type: "sign", userId: "U006", state: "pending" },
  { id: "P015", contractId: "K006", type: "countersign", userId: "U004", state: "done", content: "订阅数量与周期一致。", time: "2026-04-27 10:00" },
  { id: "P016", contractId: "K006", type: "approve", userId: "U005", state: "done", content: "通过。", time: "2026-04-28 09:30" },
  { id: "P017", contractId: "K006", type: "sign", userId: "U006", state: "done", content: "电子签订完成。", time: "2026-04-29 16:10" },
  { id: "P018", contractId: "K007", type: "countersign", userId: "U002", state: "done", content: "建议调整安全责任条款。", time: "2026-05-01 11:05" },
  { id: "P019", contractId: "K007", type: "approve", userId: "U005", state: "rejected", content: "赔付边界不清，退回重拟。", time: "2026-05-02 09:45" },
  { id: "P020", contractId: "K009", type: "countersign", userId: "U002", state: "pending" },
  { id: "P021", contractId: "K009", type: "approve", userId: "U005", state: "pending" },
  { id: "P022", contractId: "K009", type: "sign", userId: "U006", state: "pending" },
  { id: "P023", contractId: "K010", type: "countersign", userId: "U003", state: "done", content: "阶段验收建议按月拆分。", time: "2026-05-12 10:22" },
  { id: "P024", contractId: "K010", type: "approve", userId: "U005", state: "pending" },
  { id: "P025", contractId: "K010", type: "sign", userId: "U006", state: "pending" },
  { id: "P026", contractId: "K011", type: "countersign", userId: "U002", state: "done", content: "课程交付物已列明。", time: "2026-05-14 09:35" },
  { id: "P027", contractId: "K011", type: "approve", userId: "U005", state: "pending" },
  { id: "P028", contractId: "K011", type: "sign", userId: "U006", state: "pending" },
  { id: "P029", contractId: "K012", type: "countersign", userId: "U003", state: "done", content: "备件目录完整。", time: "2026-05-15 13:30" },
  { id: "P030", contractId: "K012", type: "approve", userId: "U005", state: "done", content: "通过。", time: "2026-05-16 10:20" },
  { id: "P031", contractId: "K012", type: "sign", userId: "U006", state: "pending" },
  { id: "P032", contractId: "K013", type: "countersign", userId: "U004", state: "done", content: "服务内容明确。", time: "2025-12-22 10:00" },
  { id: "P033", contractId: "K013", type: "approve", userId: "U005", state: "done", content: "通过。", time: "2025-12-23 14:00" },
  { id: "P034", contractId: "K013", type: "sign", userId: "U006", state: "done", content: "线下签订完成。", time: "2025-12-24 16:20" },
  { id: "P035", contractId: "K014", type: "countersign", userId: "U003", state: "done", content: "监理范围清楚。", time: "2026-03-12 11:00" },
  { id: "P036", contractId: "K014", type: "approve", userId: "U005", state: "done", content: "通过。", time: "2026-03-13 15:00" },
  { id: "P037", contractId: "K014", type: "sign", userId: "U006", state: "done", content: "电子签订完成。", time: "2026-03-14 10:00" },
  { id: "P038", contractId: "K015", type: "countersign", userId: "U002", state: "done", content: "费用结算依据不足。", time: "2026-04-20 10:05" },
  { id: "P039", contractId: "K015", type: "approve", userId: "U005", state: "rejected", content: "预算未确认，拒绝。", time: "2026-04-21 09:30" }
];

export const initialSignRecords: SignRecord[] = [
  { id: "S001", contractId: "K006", signDate: "2026-04-29", method: "电子签章", remark: "已归档电子合同。" },
  { id: "S002", contractId: "K013", signDate: "2025-12-24", method: "线下盖章", remark: "纸质原件存放于法务档案柜。" },
  { id: "S003", contractId: "K014", signDate: "2026-03-14", method: "电子签章", remark: "监理资料同步归档。" }
];

export const initialLogs: Log[] = Array.from({ length: 30 }, (_, index) => {
  const actions = [
    "登录系统",
    "起草合同",
    "分配合同流程人员",
    "提交会签意见",
    "完成合同定稿",
    "提交审批意见",
    "录入签订信息",
    "维护客户资料",
    "调整角色权限",
    "导出操作日志"
  ];
  const users = ["admin", "operator", "zhangmin", "liwei", "wangyan", "chenhao"];
  const day = String((index % 24) + 1).padStart(2, "0");
  const hour = String(9 + (index % 9)).padStart(2, "0");
  const minute = String((index * 7) % 60).padStart(2, "0");

  return {
    id: `L${String(index + 1).padStart(3, "0")}`,
    userName: users[index % users.length],
    content: `${actions[index % actions.length]}：${index % 3 === 0 ? "合同流程" : "基础数据"}`,
    time: `2026-05-${day} ${hour}:${minute}`
  };
});
