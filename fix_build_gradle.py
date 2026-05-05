filepath = r"C:\Users\HP\AndroidStudioProjects\BarAndGrillPOS\BarAndGrillOwnerPanel\build.gradle.kts"

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix the nested implementation
content = content.replace(
    "implementation 'implementation(\"org.xerial:sqlite-jdbc:3.45.3.0\")'",
    "implementation(\"org.xerial:sqlite-jdbc:3.45.3.0\")"
)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

print("Fixed build.gradle.kts")
