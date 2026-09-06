import { Eye, Pencil, Trash2 } from "lucide-react";
import type { ReactNode } from "react";
import type { Contract, Customer, User } from "../types";
import { formatDate } from "../utils/format";
import { StatusBadge } from "./Badge";
import { Button } from "./Button";
import { EmptyState } from "./EmptyState";

export function ContractTable({
  contracts,
  customers,
  users,
  actions,
  onView,
  onEdit,
  onDelete
}: {
  contracts: Contract[];
  customers: Customer[];
  users: User[];
  actions?: (contract: Contract) => ReactNode;
  onView?: (contract: Contract) => void;
  onEdit?: (contract: Contract) => void;
  onDelete?: (contract: Contract) => void;
}) {
  if (!contracts.length) return <EmptyState />;

  return (
    <div className="overflow-x-auto">
      <table>
        <thead>
          <tr>
            <th>合同编号</th>
            <th>合同名称</th>
            <th>客户</th>
            <th>起草人</th>
            <th>起草时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {contracts.map((contract) => (
            <tr key={contract.id}>
              <td>{contract.num}</td>
              <td className="font-medium text-text">{contract.name}</td>
              <td>{customers.find((customer) => customer.id === contract.customerId)?.name || contract.customerId}</td>
              <td>{users.find((user) => user.id === contract.drafterId)?.name || contract.drafterId}</td>
              <td>{formatDate(contract.createdAt)}</td>
              <td><StatusBadge status={contract.status} /></td>
              <td>
                <div className="flex items-center gap-2">
                  {onView ? <Button variant="text" className="min-h-8 px-2" onClick={() => onView(contract)} icon={<Eye className="h-[18px] w-[18px]" />}>查看</Button> : null}
                  {actions ? actions(contract) : null}
                  {onEdit ? <Button variant="text" className="min-h-8 px-2" onClick={() => onEdit(contract)} icon={<Pencil className="h-[18px] w-[18px]" />}>编辑</Button> : null}
                  {onDelete ? <Button variant="text" className="min-h-8 px-2 text-error" onClick={() => onDelete(contract)} icon={<Trash2 className="h-[18px] w-[18px]" />}>删除</Button> : null}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
