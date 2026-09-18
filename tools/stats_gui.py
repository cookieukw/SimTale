#!/usr/bin/env python3
"""
SimTale - Codebase & Git Diff Real-Time Inspector GUI
Interface gráfica em Tkinter para inspecionar estatísticas de código, modelos 3D,
JSONs, documentação e traduções em tempo real (Git Diff e Arquivos no Disco).
"""

import os
import sys
import subprocess
import threading
import time
import tkinter as tk
from tkinter import ttk, messagebox

# Cores do Tema Escuro (Catppuccin Mocha inspired)
BG_DARK = "#1e1e2e"
BG_CARD = "#252538"
BG_CARD_ALT = "#2a2a3e"
BORDER_COLOR = "#36364f"
TEXT_PRIMARY = "#cdd6f4"
TEXT_SECONDARY = "#a6adc8"
TEXT_MUTED = "#6c7086"

COLOR_GREEN = "#a6e3a1"
COLOR_RED = "#f38ba8"
COLOR_BLUE = "#89b4fa"
COLOR_YELLOW = "#f9e2af"
COLOR_PEACH = "#fab387"
COLOR_MAUVE = "#cba6f7"
COLOR_TEAL = "#94e2d5"

CATEGORY_COLORS = {
    "Modelos 3D (.blockymodel)": "#89b4fa",
    "Configurações & Roles (.json)": "#a6e3a1",
    "Código Java (.java)": "#fab387",
    "Documentação & Wiki (.md)": "#cba6f7",
    "Traduções (.lang, .properties)": "#f9e2af",
    "Scripts (.py)": "#94e2d5",
    "Interface UI (.ui)": "#f5c2e7",
    "Imagens & Assets (.png, .svg)": "#74c7ec",
    "Outros": "#b4befe"
}

def classify_extension(ext):
    ext = ext.lower()
    if ext == "java":
        return "Código Java (.java)"
    elif ext == "blockymodel":
        return "Modelos 3D (.blockymodel)"
    elif ext == "json":
        return "Configurações & Roles (.json)"
    elif ext in ("md", "markdown"):
        return "Documentação & Wiki (.md)"
    elif ext in ("lang", "properties"):
        return "Traduções (.lang, .properties)"
    elif ext == "py":
        return "Scripts (.py)"
    elif ext == "ui":
        return "Interface UI (.ui)"
    elif ext in ("png", "svg", "ico", "jpg", "jpeg"):
        return "Imagens & Assets (.png, .svg)"
    elif ext == "blockyanim":
        return "Animações (.blockyanim)"
    else:
        return "Outros"

class SimTaleStatsApp(tk.Tk):
    def __init__(self, repo_dir):
        super().__init__()
        self.repo_dir = os.path.abspath(repo_dir)
        self.title("SimTale — Inspetor de Código & Git Diff em Tempo Real")
        self.geometry("980x720")
        self.minsize(850, 600)
        self.configure(bg=BG_DARK)

        self.current_mode = tk.StringVar(value="git")
        self.base_branch = tk.StringVar(value="main")
        self.auto_refresh = tk.BooleanVar(value=False)
        self.refresh_interval = tk.IntVar(value=5)

        self.is_fetching = False
        self.data_cache = {}

        self.setup_styles()
        self.build_ui()
        self.start_refresh_thread()

    def setup_styles(self):
        style = ttk.Style(self)
        style.theme_use("clam")

        # Configurações gerais de widgets ttk
        style.configure(".", background=BG_DARK, foreground=TEXT_PRIMARY, font=("Sans", 10))
        style.configure("TFrame", background=BG_DARK)
        style.configure("Card.TFrame", background=BG_CARD)

        style.configure("TLabel", background=BG_DARK, foreground=TEXT_PRIMARY)
        style.configure("Card.TLabel", background=BG_CARD, foreground=TEXT_PRIMARY)
        style.configure("Muted.TLabel", background=BG_DARK, foreground=TEXT_MUTED)
        style.configure("CardMuted.TLabel", background=BG_CARD, foreground=TEXT_MUTED)

        style.configure("Header.TLabel", font=("Sans", 14, "bold"), foreground=TEXT_PRIMARY, background=BG_DARK)
        style.configure("StatVal.TLabel", font=("Sans", 18, "bold"), background=BG_CARD)
        style.configure("StatTitle.TLabel", font=("Sans", 9), foreground=TEXT_SECONDARY, background=BG_CARD)

        style.configure("Green.StatVal.TLabel", font=("Sans", 18, "bold"), foreground=COLOR_GREEN, background=BG_CARD)
        style.configure("Red.StatVal.TLabel", font=("Sans", 18, "bold"), foreground=COLOR_RED, background=BG_CARD)
        style.configure("Blue.StatVal.TLabel", font=("Sans", 18, "bold"), foreground=COLOR_BLUE, background=BG_CARD)
        style.configure("Peach.StatVal.TLabel", font=("Sans", 18, "bold"), foreground=COLOR_PEACH, background=BG_CARD)

        style.configure("TButton", background=BG_CARD_ALT, foreground=TEXT_PRIMARY, borderwidth=1, relief="flat", font=("Sans", 10, "bold"), padding=6)
        style.map("TButton", background=[("active", BORDER_COLOR), ("pressed", BG_CARD)])

        style.configure("Action.TButton", background=COLOR_BLUE, foreground="#11111b", font=("Sans", 10, "bold"), padding=6)
        style.map("Action.TButton", background=[("active", "#b4befe")])

        style.configure("Treeview",
                        background=BG_CARD,
                        foreground=TEXT_PRIMARY,
                        fieldbackground=BG_CARD,
                        rowheight=28,
                        font=("Sans", 10))
        style.configure("Treeview.Heading",
                        background=BG_CARD_ALT,
                        foreground=TEXT_SECONDARY,
                        font=("Sans", 10, "bold"),
                        relief="flat")
        style.map("Treeview", background=[("selected", BORDER_COLOR)])

    def build_ui(self):
        # 1. Top Bar / Header
        top_bar = tk.Frame(self, bg=BG_DARK, pady=10, padx=16)
        top_bar.pack(fill="x")

        title_box = tk.Frame(top_bar, bg=BG_DARK)
        title_box.pack(side="left")

        lbl_title = tk.Label(title_box, text="SimTale — Estatísticas do Repositório", font=("Sans", 15, "bold"), bg=BG_DARK, fg=TEXT_PRIMARY)
        lbl_title.pack(anchor="w")

        self.lbl_subtitle = tk.Label(title_box, text="Calculando dados...", font=("Sans", 9), bg=BG_DARK, fg=TEXT_MUTED)
        self.lbl_subtitle.pack(anchor="w")

        controls_box = tk.Frame(top_bar, bg=BG_DARK)
        controls_box.pack(side="right")

        btn_git = tk.Radiobutton(controls_box, text="Git Diff (PR)", variable=self.current_mode, value="git",
                                 command=self.trigger_refresh, bg=BG_DARK, fg=TEXT_PRIMARY, selectcolor=BG_CARD,
                                 activebackground=BG_DARK, activeforeground=TEXT_PRIMARY, font=("Sans", 10, "bold"))
        btn_git.pack(side="left", padx=6)

        btn_disk = tk.Radiobutton(controls_box, text="Arquivos no Disco", variable=self.current_mode, value="disk",
                                  command=self.trigger_refresh, bg=BG_DARK, fg=TEXT_PRIMARY, selectcolor=BG_CARD,
                                  activebackground=BG_DARK, activeforeground=TEXT_PRIMARY, font=("Sans", 10, "bold"))
        btn_disk.pack(side="left", padx=6)

        btn_refresh = tk.Button(controls_box, text="⟳ Atualizar", command=self.trigger_refresh,
                                bg=COLOR_BLUE, fg="#11111b", font=("Sans", 9, "bold"), relief="flat", padx=10, pady=4, cursor="hand2")
        btn_refresh.pack(side="left", padx=10)

        # 2. Metric Cards Box
        cards_frame = tk.Frame(self, bg=BG_DARK, padx=16, pady=4)
        cards_frame.pack(fill="x")

        self.card_1 = self.create_card(cards_frame, "ARQUIVOS ALTERADOS", "0", COLOR_BLUE)
        self.card_1.pack(side="left", fill="both", expand=True, padx=4)

        self.card_2 = self.create_card(cards_frame, "LINHAS ADICIONADAS (+)", "+0", COLOR_GREEN)
        self.card_2.pack(side="left", fill="both", expand=True, padx=4)

        self.card_3 = self.create_card(cards_frame, "LINHAS REMOVIDAS (-)", "-0", COLOR_RED)
        self.card_3.pack(side="left", fill="both", expand=True, padx=4)

        self.card_4 = self.create_card(cards_frame, "CÓDIGO JAVA PURO", "0%", COLOR_PEACH)
        self.card_4.pack(side="left", fill="both", expand=True, padx=4)

        # 3. Canvas Visual Bar (Proporção gráfica)
        bar_container = tk.Frame(self, bg=BG_DARK, padx=20, pady=10)
        bar_container.pack(fill="x")

        tk.Label(bar_container, text="Distribuição Visual do Conteúdo:", font=("Sans", 10, "bold"), bg=BG_DARK, fg=TEXT_SECONDARY).pack(anchor="w", pady=(0, 4))
        
        self.canvas_bar = tk.Canvas(bar_container, height=22, bg=BG_CARD, bd=0, highlightthickness=1, highlightbackground=BORDER_COLOR)
        self.canvas_bar.pack(fill="x")

        self.legend_frame = tk.Frame(bar_container, bg=BG_DARK)
        self.legend_frame.pack(fill="x", pady=(6, 0))

        # 4. Table / Treeview Frame
        table_frame = tk.Frame(self, bg=BG_DARK, padx=16, pady=6)
        table_frame.pack(fill="both", expand=True)

        columns = ("cat", "files", "files_pct", "add", "del", "lines_pct")
        self.tree = ttk.Treeview(table_frame, columns=columns, show="headings", selectmode="browse")

        self.tree.heading("cat", text="Categoria / Tipo de Arquivo")
        self.tree.heading("files", text="Arquivos")
        self.tree.heading("files_pct", text="% Arquivos")
        self.tree.heading("add", text="Adições (+)")
        self.tree.heading("del", text="Deleções (-)")
        self.tree.heading("lines_pct", text="% do Volume")

        self.tree.column("cat", width=260, anchor="w")
        self.tree.column("files", width=90, anchor="center")
        self.tree.column("files_pct", width=100, anchor="center")
        self.tree.column("add", width=110, anchor="e")
        self.tree.column("del", width=110, anchor="e")
        self.tree.column("lines_pct", width=110, anchor="center")

        scroll_y = ttk.Scrollbar(table_frame, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scroll_y.set)

        self.tree.pack(side="left", fill="both", expand=True)
        scroll_y.pack(side="right", fill="y")

        # 5. Bottom Status Bar
        status_bar = tk.Frame(self, bg=BG_CARD, padx=16, pady=6)
        status_bar.pack(fill="x", side="bottom")

        self.lbl_status = tk.Label(status_bar, text="Pronto.", font=("Sans", 9), bg=BG_CARD, fg=TEXT_MUTED)
        self.lbl_status.pack(side="left")

        auto_box = tk.Frame(status_bar, bg=BG_CARD)
        auto_box.pack(side="right")

        chk_auto = tk.Checkbutton(auto_box, text="Auto-atualizar (5s)", variable=self.auto_refresh,
                                  bg=BG_CARD, fg=TEXT_PRIMARY, selectcolor=BG_CARD_ALT,
                                  activebackground=BG_CARD, activeforeground=TEXT_PRIMARY, font=("Sans", 9))
        chk_auto.pack(side="left")

    def create_card(self, parent, title, initial_val, color):
        card = tk.Frame(parent, bg=BG_CARD, padx=12, pady=10, relief="flat", highlightthickness=1, highlightbackground=BORDER_COLOR)
        lbl_val = tk.Label(card, text=initial_val, font=("Sans", 16, "bold"), fg=color, bg=BG_CARD)
        lbl_val.pack(anchor="w")
        lbl_title = tk.Label(card, text=title, font=("Sans", 8, "bold"), fg=TEXT_MUTED, bg=BG_CARD)
        lbl_title.pack(anchor="w", pady=(2, 0))
        card.lbl_val = lbl_val
        card.lbl_title = lbl_title
        return card

    def trigger_refresh(self):
        if self.is_fetching:
            return
        self.is_fetching = True
        self.lbl_status.config(text="Carregando estatísticas do repositório...")
        threading.Thread(target=self.collect_data, daemon=True).start()

    def collect_data(self):
        mode = self.current_mode.get()
        base = self.base_branch.get()

        if mode == "git":
            data = self.scan_git_diff(base)
        else:
            data = self.scan_disk_files()

        self.after(0, self.update_ui, data)

    def scan_git_diff(self, base):
        categories = {}
        total_files = 0
        total_add = 0
        total_del = 0
        commits_count = 0

        try:
            # Conta commits
            cmd_commits = ["git", "rev-list", "--count", f"{base}...HEAD"]
            c_res = subprocess.run(cmd_commits, cwd=self.repo_dir, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            if c_res.returncode == 0:
                commits_count = int(c_res.stdout.strip())
        except Exception:
            commits_count = 0

        try:
            cmd = ["git", "diff", "--numstat", f"{base}...HEAD"]
            res = subprocess.run(cmd, cwd=self.repo_dir, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            if res.returncode != 0:
                return {"error": res.stderr.strip() or "Erro ao rodar git diff"}

            for line in res.stdout.strip().split("\n"):
                if not line:
                    continue
                parts = line.split("\t")
                if len(parts) != 3:
                    continue
                add_s, del_s, path = parts
                try:
                    add = int(add_s)
                    del_ = int(del_s)
                except ValueError:
                    add, del_ = 0, 0

                ext = path.split(".")[-1].lower() if "." in path else "sem_ext"
                cat = classify_extension(ext)

                if cat not in categories:
                    categories[cat] = {"files": 0, "add": 0, "del": 0}
                categories[cat]["files"] += 1
                categories[cat]["add"] += add
                categories[cat]["del"] += del_

                total_files += 1
                total_add += add
                total_del += del_

            return {
                "mode": "git",
                "commits": commits_count,
                "total_files": total_files,
                "total_add": total_add,
                "total_del": total_del,
                "categories": categories
            }
        except Exception as e:
            return {"error": str(e)}

    def scan_disk_files(self):
        categories = {}
        total_files = 0
        total_lines = 0

        ignore_dirs = {".git", "build", ".gradle", "node_modules", ".docusaurus", "libs", "wiki/node_modules"}

        try:
            for root, dirs, files in os.walk(self.repo_dir):
                dirs[:] = [d for d in dirs if d not in ignore_dirs and not any(ign in os.path.join(root, d) for ign in ignore_dirs)]
                for f in files:
                    path = os.path.join(root, f)
                    ext = f.split(".")[-1].lower() if "." in f else "sem_ext"
                    cat = classify_extension(ext)

                    lines = 0
                    try:
                        with open(path, "rb") as fp:
                            lines = sum(1 for _ in fp)
                    except Exception:
                        lines = 0

                    if cat not in categories:
                        categories[cat] = {"files": 0, "add": 0, "del": 0}
                    categories[cat]["files"] += 1
                    categories[cat]["add"] += lines

                    total_files += 1
                    total_lines += lines

            return {
                "mode": "disk",
                "total_files": total_files,
                "total_add": total_lines,
                "total_del": 0,
                "categories": categories
            }
        except Exception as e:
            return {"error": str(e)}

    def update_ui(self, data):
        self.is_fetching = False

        if "error" in data:
            self.lbl_status.config(text=f"Erro: {data['error']}", fg=COLOR_RED)
            return

        mode = data["mode"]
        tot_files = data["total_files"]
        tot_add = data["total_add"]
        tot_del = data["total_del"]
        tot_lines = tot_add + tot_del
        categories = data["categories"]

        # 1. Subtitle & Top Cards
        timestamp = time.strftime("%H:%M:%S")
        if mode == "git":
            commits = data.get("commits", 0)
            self.lbl_subtitle.config(text=f"Modo Git Diff vs '{self.base_branch.get()}' | {commits:,} commits analisados | Atualizado às {timestamp}")
            self.card_1.lbl_title.config(text="ARQUIVOS ALTERADOS")
            self.card_1.lbl_val.config(text=f"{tot_files:,}")

            self.card_2.lbl_title.config(text="LINHAS ADICIONADAS (+)")
            self.card_2.lbl_val.config(text=f"+{tot_add:,}")

            self.card_3.lbl_title.config(text="LINHAS REMOVIDAS (-)")
            self.card_3.lbl_val.config(text=f"-{tot_del:,}")
        else:
            self.lbl_subtitle.config(text=f"Modo Arquivos no Disco (Workspace Completo) | Atualizado às {timestamp}")
            self.card_1.lbl_title.config(text="TOTAL DE ARQUIVOS")
            self.card_1.lbl_val.config(text=f"{tot_files:,}")

            self.card_2.lbl_title.config(text="TOTAL DE LINHAS HOJE")
            self.card_2.lbl_val.config(text=f"{tot_add:,}")

            self.card_3.lbl_title.config(text="LINHAS DELETADAS")
            self.card_3.lbl_val.config(text="0")

        # Java %
        java_data = categories.get("Código Java (.java)", {"files": 0, "add": 0, "del": 0})
        java_metric = (java_data["add"] / tot_add * 100) if tot_add > 0 else 0
        self.card_4.lbl_title.config(text="CÓDIGO JAVA PURO")
        self.card_4.lbl_val.config(text=f"{java_metric:.1f}% (+{java_data['add']:,} lin)")

        # 2. Update Table (Treeview)
        for item in self.tree.get_children():
            self.tree.delete(item)

        # Ordenar categorias pelo volume de adições
        sorted_cats = sorted(categories.items(), key=lambda x: x[1]["add"], reverse=True)

        for cat_name, stats in sorted_cats:
            f_cnt = stats["files"]
            f_pct = (f_cnt / tot_files * 100) if tot_files > 0 else 0
            add_cnt = stats["add"]
            del_cnt = stats["del"]
            lines_pct = (add_cnt / tot_add * 100) if tot_add > 0 else 0

            self.tree.insert("", "end", values=(
                cat_name,
                f"{f_cnt:,}",
                f"{f_pct:.2f}%",
                f"+{add_cnt:,}",
                f"-{del_cnt:,}" if mode == "git" else "-",
                f"{lines_pct:.2f}%"
            ))

        # 3. Draw Visual Bar & Legend
        self.draw_proportions(sorted_cats, tot_add)

        self.lbl_status.config(text=f"Pronto. {tot_files:,} arquivos e {tot_add:,} adições computadas.", fg=TEXT_MUTED)

    def draw_proportions(self, sorted_cats, total_metric):
        self.canvas_bar.delete("all")
        width = self.canvas_bar.winfo_width()
        if width <= 1:
            width = 900
        height = 22

        if total_metric <= 0:
            return

        cur_x = 0
        # Limpar legenda anterior
        for widget in self.legend_frame.winfo_children():
            widget.destroy()

        legend_row = tk.Frame(self.legend_frame, bg=BG_DARK)
        legend_row.pack(fill="x")

        for cat_name, stats in sorted_cats:
            val = stats["add"]
            if val <= 0:
                continue
            ratio = val / total_metric
            seg_width = ratio * width

            color = CATEGORY_COLORS.get(cat_name, "#7f849c")

            if seg_width >= 1:
                self.canvas_bar.create_rectangle(cur_x, 0, cur_x + seg_width, height, fill=color, outline="")
                cur_x += seg_width

            # Adicionar badge na legenda
            pct = ratio * 100
            if pct >= 0.2:  # Mostra na legenda os relevantes
                badge = tk.Frame(legend_row, bg=BG_DARK, padx=6)
                badge.pack(side="left", pady=2)
                dot = tk.Label(badge, text="■", fg=color, bg=BG_DARK, font=("Sans", 10))
                dot.pack(side="left")
                txt = tk.Label(badge, text=f"{cat_name}: {pct:.1f}%", fg=TEXT_SECONDARY, bg=BG_DARK, font=("Sans", 8))
                txt.pack(side="left", padx=2)

    def start_refresh_thread(self):
        def loop():
            time.sleep(0.3)
            self.trigger_refresh()
            while True:
                time.sleep(self.refresh_interval.get())
                if self.auto_refresh.get():
                    self.trigger_refresh()
        threading.Thread(target=loop, daemon=True).start()

if __name__ == "__main__":
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    app = SimTaleStatsApp(repo_root)
    app.mainloop()
