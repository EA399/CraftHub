import subprocess
import threading
import tkinter as tk
from tkinter import messagebox
import customtkinter as ctk
import minecraft_launcher_lib

# Modernes Design (Dark Mode)
ctk.set_appearance_mode("Dark")
ctk.set_default_color_theme("blue")


class CraftHubLauncher(ctk.CTk):

  def __init__(self):
    super().__init__()

    self.title("CraftHub - Minecraft Launcher")
    self.geometry("750x500")
    self.resizable(False, False)

    # Minecraft Standard-Verzeichnis (%appdata%\.minecraft bzw. angepasst)
    self.minecraft_directory = (
        minecraft_launcher_lib.utils.get_minecraft_directory()
    )

    # --- UI LAYOUT (Sidebar & Hauptfenster) ---
    self.sidebar = ctk.CTkFrame(self, width=200, corner_radius=0)
    self.sidebar.pack(side="left", fill="y")

    self.logo_label = ctk.CTkLabel(
        self.sidebar,
        text="CraftHub",
        font=ctk.CTkFont(size=22, weight="bold"),
    )
    self.logo_label.pack(padx=20, pady=30)

    self.btn_play_tab = ctk.CTkButton(
        self.sidebar, text="Spielen", command=self.show_play_screen
    )
    self.btn_play_tab.pack(padx=20, pady=10, fill="x")

    self.main_frame = ctk.CTkFrame(self, fg_color="transparent")
    self.main_frame.pack(side="right", expand=True, fill="both", padx=20, pady=20)

    self.show_play_screen()

  def clear_main_frame(self):
    for widget in self.main_frame.winfo_children():
      widget.destroy()

  def show_play_screen(self):
    self.clear_main_frame()

    title = ctk.CTkLabel(
        self.main_frame,
        text="Willkommen bei CraftHub!",
        font=ctk.CTkFont(size=20, weight="bold"),
    )
    title.pack(anchor="w", pady=(10, 20))

    # Benutzername / Offline-Login
    lbl_user = ctk.CTkLabel(self.main_frame, text="Spielername:")
    lbl_user.pack(anchor="w")

    self.entry_user = ctk.CTkEntry(
        self.main_frame, placeholder_text="DeinName", width=300
    )
    self.entry_user.pack(anchor="w", pady=(5, 15))
    self.entry_user.insert(0, "Player123")

    # Versionsauswahl (lädt automatisch die neuesten Releases)
    lbl_version = ctk.CTkLabel(self.main_frame, text="Minecraft Version:")
    lbl_version.pack(anchor="w")

    try:
      versions = [
          v["id"]
          for v in minecraft_launcher_lib.utils.get_version_list()
          if v["type"] == "release"
      ][:25]
    except Exception:
      versions = ["1.20.4"]

    self.version_menu = ctk.CTkComboBox(
        self.main_frame, values=versions, width=300
    )
    self.version_menu.pack(anchor="w", pady=(5, 20))
    if versions:
      self.version_menu.set(versions[0])

    # Status-Anzeige für Download / Start
    self.status_label = ctk.CTkLabel(
        self.main_frame, text="", text_color="gray"
    )
    self.status_label.pack(anchor="w", pady=5)

    # Start-Button
    self.btn_launch = ctk.CTkButton(
        self.main_frame,
        text="SPIEL STARTEN",
        fg_color="green",
        hover_color="darkgreen",
        font=ctk.CTkFont(size=16, weight="bold"),
        command=self.start_launch_thread,
        width=200,
        height=45,
    )
    self.btn_launch.pack(anchor="w", pady=10)

  def start_launch_thread(self):
    # Startet den Prozess in einem Hintergrund-Thread, damit das UI nicht einfriert
    threading.Thread(target=self.launch_game, daemon=True).start()

  def launch_game(self):
    username = self.entry_user.get().strip()
    version = self.version_menu.get()

    if not username:
      messagebox.showerror("Fehler", "Bitte gib einen gültigen Benutzernamen ein!")
      return

    self.btn_launch.configure(state="disabled", text="Lade Dateien...")
    self.status_label.configure(
        text="Prüfe und lade Minecraft herunter (kann beim 1. Mal dauern)..."
    )

    try:
      # 1. Minecraft Version automatisch herunterladen & installieren
      minecraft_launcher_lib.install.install_minecraft_version(
          version, self.minecraft_directory, callback=self.update_progress
      )

      self.status_label.configure(text="Starte Minecraft...")

      # 2. Login-Optionen definieren (Offline-Modus als Basis)
      options = {
          "username": username,
          "uuid": "00000000-0000-0000-0000-000000000000",
          "token": "00000000-0000-0000-0000-000000000000",
      }

      # 3. Startbefehl generieren und Spiel starten
      minecraft_command = (
          minecraft_launcher_lib.command.get_minecraft_command(
              version, self.minecraft_directory, options
          )
      )
      subprocess.Popen(minecraft_command)

      self.status_label.configure(text="Spiel erfolgreich gestartet!")
      self.btn_launch.configure(
          state="normal", text="SPIEL STARTEN", fg_color="green"
      )

    except Exception as e:
      messagebox.showerror(
          "Fehler beim Start", f"Ein Fehler ist aufgetreten:\n{str(e)}"
      )
      self.status_label.configure(text="Fehler beim Starten.")
      self.btn_launch.configure(
          state="normal", text="SPIEL STARTEN", fg_color="green"
      )

  def update_progress(self, progress):
    self.status_label.configure(
        text=f"Download läuft: {progress.get('name', 'Datei')}..."
    )


if __name__ == "__main__":
  app = CraftHubLauncher()
  app.mainloop()