import { ShieldAlert } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "../components/Button";

export function ForbiddenPage() {
  return (
    <div className="grid min-h-[calc(100vh-128px)] place-items-center">
      <div className="text-center">
        <ShieldAlert className="mx-auto h-12 w-12 text-primary" />
        <h1 className="mb-2 mt-5 text-title font-semibold text-text">403</h1>
        <p className="mb-6 text-body text-muted">当前账号没有访问该页面的权限。</p>
        <Link to="/dashboard"><Button variant="secondary">返回工作台</Button></Link>
      </div>
    </div>
  );
}
